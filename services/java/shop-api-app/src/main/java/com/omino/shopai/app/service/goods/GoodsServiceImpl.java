package com.omino.shopai.app.service.goods;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.adapter.platform.IPlatformGoods;
import com.omino.shopai.app.dto.ConvertedLinkDto;
import com.omino.shopai.app.dto.GoodsCardDto;
import com.omino.shopai.app.dto.GoodsDetailDto;
import com.omino.shopai.app.dto.PromotionLinkDto;
import com.omino.shopai.entity.AppUserInstalledApp;
import com.omino.shopai.entity.ClickEvent;
import com.omino.shopai.entity.GoodsSnapshot;
import com.omino.shopai.entity.PlatformAuthorization;
import com.omino.shopai.entity.UserEvent;
import com.omino.shopai.enums.AuthStatus;
import com.omino.shopai.enums.Platform;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.exception.PlatformCallException;
import com.omino.shopai.mapper.AppUserInstalledAppMapper;
import com.omino.shopai.mapper.ClickEventMapper;
import com.omino.shopai.mapper.GoodsSnapshotMapper;
import com.omino.shopai.mapper.PlatformAuthorizationMapper;
import com.omino.shopai.mapper.UserEventMapper;
import com.omino.shopai.support.Ids;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 商品详情、搜索和点击后转链。详情先读短缓存，转链成功才记点击。
 */
@Service
public class GoodsServiceImpl implements IGoodsService {

    private static final Duration RELATION_TTL = Duration.ofMinutes(10);

    private final GoodsSnapshotMapper snapshots;
    private final List<IPlatformGoods> clients;
    private final IGoodsDetailCache cache;
    private final UserEventMapper events;
    private final ObjectMapper json;
    private final PlatformAuthorizationMapper authorizations;
    private final StringRedisTemplate redis;
    private final AppUserInstalledAppMapper installedApps;
    private final ClickEventMapper clicks;

    /**
     * 详情、搜索和转链共用同一份平台适配器。
     *
     * @param snapshots 商品快照表
     * @param clients 各平台适配器
     * @param cache 详情短缓存
     * @param events 浏览事件表
     * @param json 缓存序列化
     * @param authorizations 授权表，用来读推广身份
     * @param redis 推广身份缓存
     * @param installedApps 已安装的电商 APP
     * @param clicks 点击记录
     */
    public GoodsServiceImpl(
            GoodsSnapshotMapper snapshots,
            List<IPlatformGoods> clients,
            IGoodsDetailCache cache,
            UserEventMapper events,
            ObjectMapper json,
            PlatformAuthorizationMapper authorizations,
            StringRedisTemplate redis,
            AppUserInstalledAppMapper installedApps,
            ClickEventMapper clicks) {
        this.snapshots = snapshots;
        this.clients = clients;
        this.cache = cache;
        this.events = events;
        this.json = json;
        this.authorizations = authorizations;
        this.redis = redis;
        this.installedApps = installedApps;
        this.clicks = clicks;
    }

    /**
     * 读取详情。有商品号时只读快照，否则按平台商品号走缓存和联盟。
     *
     * @param userId 当前用户
     * @param goodsId 本系统商品号，可空
     * @param platform 平台码，没有商品号时必填
     * @param platformGoodsId 平台商品号，没有商品号时必填
     * @return 商品详情
     */
    @Override
    public GoodsDetailDto detail(String userId, String goodsId, String platform, String platformGoodsId) {
        if (goodsId != null && !goodsId.isBlank()) {
            GoodsSnapshot found = findById(goodsId)
                    .orElseThrow(() -> new ApiBusinessException(ErrorCodes.NOT_FOUND, "商品不存在"));
            return publish(userId, found);
        }
        if (platform == null || platform.isBlank() || platformGoodsId == null || platformGoodsId.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "商品不能为空");
        }
        Platform parsed = requirePlatform(platform);
        Optional<String> cached = cache.get(parsed.code(), platformGoodsId);
        if (cached.isPresent()) {
            GoodsDetailDto detail = read(cached.get());
            if (detail != null) {
                browse(userId, detail.goodsId(), detail.platform(), detail.platformGoodsId(), detail.title());
                return detail;
            }
        }
        GoodsSnapshot snapshot = findByPlatform(platform, platformGoodsId)
                .orElseGet(() -> load(userId, parsed, platformGoodsId));
        return publish(userId, snapshot);
    }

    /**
     * 在一个平台上按关键词搜品。没有推广身份则不出站。
     *
     * @param userId 当前用户
     * @param platform 平台码
     * @param keyword 关键词
     * @return 商品卡
     */
    @Override
    public List<GoodsCardDto> search(String userId, String platform, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "关键词不能为空");
        }
        Platform parsed = requirePlatform(platform);
        String promotionId = requireRelation(userId, parsed);
        try {
            List<GoodsCardDto> cards = new ArrayList<>();
            for (GoodsSnapshot found : client(parsed).search(keyword.trim(), userId, promotionId)) {
                if (found.getPlatform() == null) {
                    found.setPlatform(parsed);
                }
                cards.add(toCard(save(found)));
            }
            return cards;
        } catch (PlatformCallException ex) {
            throw upstream();
        }
    }

    /**
     * 校验授权和推广身份后生成购买链接，成功才记点击。
     *
     * @param userId 当前用户
     * @param goodsId 本系统商品号，可空
     * @param platform 平台码，没有商品号时必填
     * @param platformGoodsId 平台商品号，没有商品号时必填
     * @param preferApp 是否优先打开已安装的电商 APP；空按优先
     * @return 购买链接
     */
    @Override
    public ConvertedLinkDto convert(
            String userId,
            String goodsId,
            String platform,
            String platformGoodsId,
            Boolean preferApp) {
        String resolved = platform;
        if ((resolved == null || resolved.isBlank()) && goodsId != null && !goodsId.isBlank()) {
            resolved = findById(goodsId)
                    .map(GoodsSnapshot::getPlatform)
                    .map(Platform::code)
                    .orElse(null);
        }
        Platform parsed = requirePlatform(resolved);
        // 京东没有用户授权。其它三个平台必须先是已授权，否则不能拿推广身份去转链。
        if (parsed != Platform.JD && !authorized(userId, parsed.code())) {
            throw new ApiBusinessException(ErrorCodes.NEED_AUTH, "请先完成联盟授权", "NEED_AUTH");
        }
        String promotionId = relationId(userId, parsed.relationPlatform().code())
                .filter(id -> !id.isBlank())
                .orElseThrow(() -> new ApiBusinessException(ErrorCodes.NEED_AUTH, "请先完成联盟授权", "NEED_AUTH"));
        GoodsSnapshot goods = resolve(goodsId, resolved, platformGoodsId);
        String platformCode = goods.getPlatform() == null ? null : goods.getPlatform().code();
        boolean openInApp = !Boolean.FALSE.equals(preferApp) && installed(userId, platformCode);
        ConvertedLinkDto link;
        try {
            link = issue(goods, openInApp, userId, promotionId);
        } catch (PlatformCallException ex) {
            throw new ApiBusinessException(ErrorCodes.UPSTREAM_PLATFORM, "平台查询失败");
        }
        click(userId, platformCode, goods.getId(), goods.getPlatformGoodsId());
        return link;
    }

    /**
     * 已授权之后才向官方要购买链接。未安装对应 APP 时不下发 deeplink。
     *
     * @param goods 要转链的商品
     * @param openInApp 是否下发 deeplink
     * @param userId 当前用户
     * @param promotionId 推广身份
     * @return 购买链接
     */
    @Override
    public ConvertedLinkDto issue(GoodsSnapshot goods, boolean openInApp, String userId, String promotionId) {
        Platform platform = goods.getPlatform();
        IPlatformGoods client = null;
        for (IPlatformGoods candidate : clients) {
            if (platform != null && platform == candidate.platform()) {
                client = candidate;
                break;
            }
        }
        if (client == null) {
            throw new PlatformCallException();
        }
        PromotionLinkDto link = client.convert(goods, openInApp, userId, promotionId);
        String deeplink = openInApp ? link.deeplink() : null;
        String expiresAt = OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).plusHours(1).toString();
        return new ConvertedLinkDto(
                goods.getId(),
                platform == null ? null : platform.code(),
                link.clickUrl(),
                deeplink,
                link.universalLink(),
                link.fallbackHttpUrl(),
                expiresAt);
    }

    /**
     * 从商品号或请求里的平台得到快照。认不出则拒绝。
     *
     * @param goodsId 本系统商品号，可空
     * @param platform 平台码
     * @param platformGoodsId 平台商品号
     * @return 商品快照
     */
    private GoodsSnapshot resolve(String goodsId, String platform, String platformGoodsId) {
        if (goodsId != null && !goodsId.isBlank()) {
            return findById(goodsId)
                    .orElseThrow(() -> new ApiBusinessException(ErrorCodes.NOT_FOUND, "商品不存在"));
        }
        if (platformGoodsId == null || platformGoodsId.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "商品不能为空");
        }
        return findByPlatform(platform, platformGoodsId)
                .orElseThrow(() -> new ApiBusinessException(ErrorCodes.NOT_FOUND, "商品不存在"));
    }

    /**
     * 缓存和快照都没有时向联盟要详情。没有推广身份则不出站。
     *
     * @param userId 当前用户
     * @param platform 平台
     * @param platformGoodsId 平台商品号
     * @return 已落库的快照
     */
    private GoodsSnapshot load(String userId, Platform platform, String platformGoodsId) {
        String promotionId = requireRelation(userId, platform);
        try {
            GoodsSnapshot fetched = client(platform).detail(platformGoodsId, userId, promotionId);
            if (fetched == null) {
                throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "商品不存在");
            }
            fetched.setPlatform(platform);
            if (fetched.getPlatformGoodsId() == null || fetched.getPlatformGoodsId().isBlank()) {
                fetched.setPlatformGoodsId(platformGoodsId);
            }
            return save(fetched);
        } catch (PlatformCallException ex) {
            throw upstream();
        }
    }

    /**
     * 按平台取适配器。未启用则按上游失败返回。
     *
     * @param platform 平台
     * @return 该平台的客户端
     */
    private IPlatformGoods client(Platform platform) {
        for (IPlatformGoods candidate : clients) {
            if (platform == candidate.platform()) {
                return candidate;
            }
        }
        throw upstream();
    }

    /**
     * 联盟失败的统一业务异常。
     *
     * @return 50010
     */
    private static ApiBusinessException upstream() {
        return new ApiBusinessException(ErrorCodes.UPSTREAM_PLATFORM, "平台查询失败");
    }

    /**
     * 没有推广身份就停在这里，不创建官方客户端。拼多多读京东那条。
     *
     * @param userId 当前用户
     * @param platform 请求的平台
     * @return 非空推广身份
     */
    private String requireRelation(String userId, Platform platform) {
        return relationId(userId, platform.relationPlatform().code())
                .filter(id -> !id.isBlank())
                .orElseThrow(() -> new ApiBusinessException(ErrorCodes.NEED_AUTH, "请先完成联盟授权", "NEED_AUTH"));
    }

    /**
     * 把快照写入短缓存并记浏览。
     *
     * @param userId 当前用户
     * @param snapshot 已落库的快照
     * @return 详情
     */
    private GoodsDetailDto publish(String userId, GoodsSnapshot snapshot) {
        GoodsDetailDto detail = toDetail(snapshot);
        String platformCode = snapshot.getPlatform() == null ? null : snapshot.getPlatform().code();
        cache.put(platformCode, snapshot.getPlatformGoodsId(), write(detail));
        browse(userId, snapshot.getId(), platformCode, snapshot.getPlatformGoodsId(), snapshot.getTitle());
        return detail;
    }

    /**
     * 平台码必须落在四个枚举里。
     *
     * @param platform 请求里的平台码
     * @return 平台枚举
     */
    static Platform requirePlatform(String platform) {
        try {
            Platform parsed = Platform.fromCode(platform);
            if (parsed == null) {
                throw new ApiBusinessException(ErrorCodes.VALIDATION, "平台不正确");
            }
            return parsed;
        } catch (IllegalArgumentException ex) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "平台不正确");
        }
    }

    /**
     * 把快照收成详情。对外平台字段用枚举码。
     *
     * @param snapshot 商品快照
     * @return 详情
     */
    private static GoodsDetailDto toDetail(GoodsSnapshot snapshot) {
        List<String> images = snapshot.getImageUrl() == null || snapshot.getImageUrl().isBlank()
                ? List.of()
                : List.of(snapshot.getImageUrl());
        return new GoodsDetailDto(
                snapshot.getId(),
                snapshot.getPlatform() == null ? null : snapshot.getPlatform().code(),
                snapshot.getPlatformGoodsId(),
                snapshot.getTitle(),
                null,
                images,
                money(snapshot.getPrice()),
                money(snapshot.getCouponPrice()),
                null,
                snapshot.getShopName(),
                snapshot.getSalesCount(),
                snapshot.getReviewCount(),
                snapshot.getGoodsUrl(),
                null,
                null,
                true);
    }

    /**
     * 把快照收成商品卡。
     *
     * @param snapshot 商品快照
     * @return 商品卡
     */
    static GoodsCardDto toCard(GoodsSnapshot snapshot) {
        return new GoodsCardDto(
                snapshot.getId(),
                snapshot.getPlatform() == null ? null : snapshot.getPlatform().code(),
                snapshot.getPlatformGoodsId(),
                snapshot.getTitle(),
                snapshot.getImageUrl(),
                money(snapshot.getPrice()),
                money(snapshot.getCouponPrice()),
                snapshot.getSalesCount(),
                snapshot.getReviewCount(),
                snapshot.getGoodsUrl(),
                snapshot.getShopName());
    }

    /**
     * 金额收成两位小数字符串。空值保持空。
     *
     * @param amount 金额，可空
     * @return 两位小数；空输入返回空
     */
    private static String money(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 读短缓存。反序列化失败则当未命中。
     *
     * @param body 缓存里的 JSON
     * @return 详情；读不出则空
     */
    private GoodsDetailDto read(String body) {
        try {
            return json.readValue(body, GoodsDetailDto.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    /**
     * 写入短缓存。写失败不影响本次详情返回。
     *
     * @param detail 详情
     * @return JSON；写不出则空串
     */
    private String write(GoodsDetailDto detail) {
        try {
            return json.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            return "";
        }
    }

    /**
     * 按主键读快照。
     *
     * @param id 商品号
     * @return 快照；没有则空
     */
    protected Optional<GoodsSnapshot> findById(String id) {
        return Optional.ofNullable(snapshots.selectById(id));
    }

    /**
     * 按平台和平台商品号读快照。
     *
     * @param platform 平台码
     * @param platformGoodsId 平台商品号
     * @return 快照；没有则空
     */
    protected Optional<GoodsSnapshot> findByPlatform(String platform, String platformGoodsId) {
        return Optional.ofNullable(snapshots.selectOne(new LambdaQueryWrapper<GoodsSnapshot>()
                .eq(GoodsSnapshot::getPlatform, platform)
                .eq(GoodsSnapshot::getPlatformGoodsId, platformGoodsId)));
    }

    /**
     * 该用户是否已授权这个平台。
     *
     * @param userId 当前用户
     * @param platform 平台码
     * @return 状态为已授权时为真
     */
    protected boolean authorized(String userId, String platform) {
        Platform parsed = platformOf(platform);
        if (parsed == null) {
            return false;
        }
        PlatformAuthorization row = authorizations.selectOne(new LambdaQueryWrapper<PlatformAuthorization>()
                .eq(PlatformAuthorization::getUserId, userId)
                .eq(PlatformAuthorization::getPlatform, parsed));
        return row != null && AuthStatus.AUTHORIZED == row.getStatus();
    }

    /**
     * 端上是否报过已安装对应 APP。
     *
     * @param userId 当前用户
     * @param platform 平台码
     * @return 已安装时为真
     */
    protected boolean installed(String userId, String platform) {
        Platform parsed = platformOf(platform);
        if (parsed == null) {
            return false;
        }
        AppUserInstalledApp row = installedApps.selectOne(new LambdaQueryWrapper<AppUserInstalledApp>()
                .eq(AppUserInstalledApp::getUserId, userId)
                .eq(AppUserInstalledApp::getPlatform, parsed));
        return row != null && Integer.valueOf(1).equals(row.getInstalled());
    }

    /**
     * 记一次转链点击，供后续归因。
     *
     * @param userId 当前用户
     * @param platform 平台码
     * @param goodsId 商品号
     * @param platformGoodsId 平台商品号
     */
    protected void click(String userId, String platform, String goodsId, String platformGoodsId) {
        ClickEvent event = new ClickEvent();
        event.setId(Ids.ulid());
        event.setUserId(userId);
        event.setPlatform(Platform.fromCode(platform));
        event.setGoodsId(goodsId);
        event.setPlatformGoodsId(platformGoodsId);
        event.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        clicks.insert(event);
    }

    /**
     * 按平台商品号插入或更新快照。已有行沿用原来的主键。
     *
     * @param snapshot 待保存的快照
     * @return 落库后的快照
     */
    protected GoodsSnapshot save(GoodsSnapshot snapshot) {
        if (snapshot.getId() == null || snapshot.getId().isBlank()) {
            snapshot.setId(Ids.ulid());
        }
        if (snapshot.getUpdatedAt() == null) {
            snapshot.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        }
        GoodsSnapshot existing = findByPlatform(
                snapshot.getPlatform() == null ? null : snapshot.getPlatform().code(),
                snapshot.getPlatformGoodsId()).orElse(null);
        if (existing == null) {
            snapshots.insert(snapshot);
            return snapshot;
        }
        snapshot.setId(existing.getId());
        snapshots.updateById(snapshot);
        return snapshot;
    }

    /**
     * 记一次浏览。平台写成枚举，事件种类仍是字符串。
     *
     * @param userId 当前用户
     * @param goodsId 商品号
     * @param platform 平台码
     * @param platformGoodsId 平台商品号
     * @param title 标题
     */
    protected void browse(String userId, String goodsId, String platform, String platformGoodsId, String title) {
        UserEvent event = new UserEvent();
        event.setId(Ids.ulid());
        event.setUserId(userId);
        event.setKind("browse");
        event.setPlatform(Platform.fromCode(platform));
        event.setGoodsId(goodsId);
        event.setPlatformGoodsId(platformGoodsId);
        event.setTitle(title);
        event.setPayload("{}");
        event.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        // payload 是 jsonb。直接 insert 会按字符串绑定，详情记浏览就会变成 50000。
        events.insertEvent(
                event.getId(),
                event.getUserId(),
                event.getKind(),
                platform,
                event.getGoodsId(),
                event.getPlatformGoodsId(),
                event.getTitle(),
                event.getPayload(),
                event.getCreatedAt());
    }

    /**
     * 当前用户在该平台的推广身份。先读缓存，没有再查表，查到才写入，避免把「还没有」卡住十分钟。
     *
     * @param userId 当前用户
     * @param platform 平台码。拼多多的调用方应传入京东
     * @return 非空的 relation_id；没有则空
     */
    protected Optional<String> relationId(String userId, String platform) {
        return cached(
                userId,
                platform,
                key -> redis.opsForValue().get(key),
                (key, value) -> redis.opsForValue().set(key, value, RELATION_TTL),
                this::loadRelation);
    }

    /**
     * 先读缓存，未命中再查表。
     *
     * @param userId 当前用户
     * @param platform 平台码
     * @param read 按 key 读缓存，没有则空
     * @param write 把查到的身份写入缓存
     * @param load 缓存未命中时查表
     * @return 推广身份；没有则空
     */
    public static Optional<String> cached(
            String userId,
            String platform,
            Function<String, String> read,
            BiConsumer<String, String> write,
            BiFunction<String, String, Optional<String>> load) {
        if (userId == null || userId.isBlank() || platform == null || platform.isBlank()) {
            return Optional.empty();
        }
        String key = "union:relation:" + userId + ":" + platform;
        String hit = read.apply(key);
        if (hit != null && !hit.isBlank()) {
            return Optional.of(hit);
        }
        Optional<String> found = load.apply(userId, platform).filter(id -> !id.isBlank());
        found.ifPresent(id -> write.accept(key, id));
        return found;
    }

    /**
     * 从表里读推广身份。
     *
     * @param userId 当前用户
     * @param platform 平台码
     * @return 非空 relation_id；没有或平台码非法则空
     */
    private Optional<String> loadRelation(String userId, String platform) {
        Platform parsed = platformOf(platform);
        if (parsed == null || userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        PlatformAuthorization row = authorizations.selectOne(new LambdaQueryWrapper<PlatformAuthorization>()
                .eq(PlatformAuthorization::getUserId, userId)
                .eq(PlatformAuthorization::getPlatform, parsed));
        if (row == null || row.getRelationId() == null || row.getRelationId().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(row.getRelationId());
    }

    /**
     * 把请求里的平台码还原成枚举。认不出则空，调用方按未授权处理。
     *
     * @param platform 平台码
     * @return 枚举；空串或无法识别时为空
     */
    private static Platform platformOf(String platform) {
        try {
            return Platform.fromCode(platform);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
