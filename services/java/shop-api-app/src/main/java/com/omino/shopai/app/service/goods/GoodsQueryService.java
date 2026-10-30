package com.omino.shopai.app.service.goods;

import com.omino.shopai.enums.Platform;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.adapter.platform.PlatformCallException;
import com.omino.shopai.app.adapter.platform.PlatformGoodsClient;
import com.omino.shopai.entity.GoodsSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
/**
 * GoodsQueryService 编排用例，事务和校验放在这里。
 */

@Service
public class GoodsQueryService {

    static final Set<String> PLATFORMS = Set.of("taobao", "jd", "pdd", "vip");

    private final GoodsSnapshotStore snapshots;
    private final List<PlatformGoodsClient> clients;
    private final GoodsDetailCache cache;
    private final GoodsLibraryStore library;
    private final ObjectMapper json;
    private final UnionRelation relations;
/**
 * 详情先读短缓存，未命中再调联盟并记浏览。
 */

    public GoodsQueryService(
            GoodsSnapshotStore snapshots,
            List<PlatformGoodsClient> clients,
            GoodsDetailCache cache,
            GoodsLibraryStore library,
            ObjectMapper json,
            UnionRelation relations) {
        this.snapshots = snapshots;
        this.clients = clients;
        this.cache = cache;
        this.library = library;
        this.json = json;
        this.relations = relations;
    }
/**
 * 读取详情。
 */

    public GoodsDetail detail(String userId, String goodsId, String platform, String platformGoodsId) {
        if (goodsId != null && !goodsId.isBlank()) {
            GoodsSnapshot found = snapshots.findById(goodsId)
                    .orElseThrow(() -> new ApiBusinessException(ErrorCodes.NOT_FOUND, "商品不存在"));
            return publish(userId, found);
        }
        if (platform == null || platform.isBlank() || platformGoodsId == null || platformGoodsId.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "商品不能为空");
        }
        requirePlatform(platform);
        Optional<String> cached = cache.get(platform, platformGoodsId);
        if (cached.isPresent()) {
            GoodsDetail detail = read(cached.get());
            if (detail != null) {
                library.browse(userId, detail.goodsId(), detail.platform(), detail.platformGoodsId(), detail.title());
                return detail;
            }
        }
        GoodsSnapshot snapshot = snapshots.findByPlatform(platform, platformGoodsId)
                .orElseGet(() -> load(userId, platform, platformGoodsId));
        return publish(userId, snapshot);
    }
/**
 * 按条件检索。
 */

    public List<GoodsCard> search(String userId, String platform, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "关键词不能为空");
        }
        requirePlatform(platform);
        String promotionId = requireRelation(userId, platform);
        try {
            List<GoodsCard> cards = new ArrayList<>();
            for (GoodsSnapshot found : client(platform).search(keyword.trim(), userId, promotionId)) {
                if (found.getPlatform() == null) {
                    found.setPlatform(Platform.fromCode(platform));
                }
                cards.add(toCard(snapshots.save(found)));
            }
            return cards;
        } catch (PlatformCallException ex) {
            throw upstream();
        }
    }
/**
 * 装载调用所需的数据。
 */

    private GoodsSnapshot load(String userId, String platform, String platformGoodsId) {
        String promotionId = requireRelation(userId, platform);
        try {
            GoodsSnapshot fetched = client(platform).detail(platformGoodsId, userId, promotionId);
            if (fetched == null) {
                throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "商品不存在");
            }
            fetched.setPlatform(Platform.fromCode(platform));
            if (fetched.getPlatformGoodsId() == null || fetched.getPlatformGoodsId().isBlank()) {
                fetched.setPlatformGoodsId(platformGoodsId);
            }
            return snapshots.save(fetched);
        } catch (PlatformCallException ex) {
            throw upstream();
        }
    }
/**
 * 按平台取适配器。未启用则按上游失败返回。
 */

    private PlatformGoodsClient client(String platform) {
        for (PlatformGoodsClient candidate : clients) {
            if (platform.equals(candidate.platform())) {
                return candidate;
            }
        }
        throw upstream();
    }

    private static ApiBusinessException upstream() {
        return new ApiBusinessException(ErrorCodes.UPSTREAM_PLATFORM, "平台查询失败");
    }

    /** 没有推广身份就停在这里，不创建官方客户端。拼多多读京东那条。 */
    private String requireRelation(String userId, String platform) {
        return relations.find(userId, UnionIds.source(platform))
                .filter(id -> !id.isBlank())
                .orElseThrow(() -> new ApiBusinessException(ErrorCodes.NEED_AUTH, "请先完成联盟授权", "NEED_AUTH"));
    }
/**
 * 发布。
 */

    private GoodsDetail publish(String userId, GoodsSnapshot snapshot) {
        GoodsDetail detail = toDetail(snapshot);
        String platformCode = snapshot.getPlatform() == null ? null : snapshot.getPlatform().code();
        cache.put(platformCode, snapshot.getPlatformGoodsId(), write(detail));
        library.browse(userId, snapshot.getId(), platformCode, snapshot.getPlatformGoodsId(), snapshot.getTitle());
        return detail;
    }
/**
 * 平台码必须落在四个枚举里。
 */

    static void requirePlatform(String platform) {
        if (platform == null || !PLATFORMS.contains(platform)) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "平台不正确");
        }
    }
/**
 * 把快照收成详情。对外平台字段用枚举码。
 */

    static GoodsDetail toDetail(GoodsSnapshot snapshot) {
        List<String> images = snapshot.getImageUrl() == null || snapshot.getImageUrl().isBlank()
                ? List.of()
                : List.of(snapshot.getImageUrl());
        return new GoodsDetail(
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

    static GoodsCard toCard(GoodsSnapshot snapshot) {
        return new GoodsCard(
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
 * 金额收成两位小数字符串。空值按零。
 */

    static String money(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
/**
 * 读短缓存。反序列化失败则当未命中。
 */

    private GoodsDetail read(String body) {
        try {
            return json.readValue(body, GoodsDetail.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
/**
 * 写入短缓存。写失败不影响本次详情返回。
 */

    private String write(GoodsDetail detail) {
        try {
            return json.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            return "";
        }
    }
}
