package com.omino.shopai.app.service.goods;

import com.omino.shopai.app.dto.GoodsCardDto;

import com.omino.shopai.app.dto.GoodsDetailDto;

import com.omino.shopai.enums.Platform;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.exception.PlatformCallException;
import com.omino.shopai.app.adapter.platform.IPlatformGoods;
import com.omino.shopai.entity.GoodsSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
/**
 * 商品详情与搜索。详情先读短缓存，未命中再调联盟并记浏览。
 */

@Service
public class GoodsQueryService {

    private final IGoodsSnapshot snapshots;
    private final List<IPlatformGoods> clients;
    private final IGoodsDetailCache cache;
    private final IGoodsLibrary library;
    private final ObjectMapper json;
    private final IGoodsGate gate;

    /**
     * 详情先读短缓存，未命中再调联盟并记浏览。
     *
     * @param snapshots 商品快照
     * @param clients 各平台适配器
     * @param cache 详情短缓存
     * @param library 浏览记录
     * @param json 缓存序列化
     * @param gate 推广身份
     */
    public GoodsQueryService(
            IGoodsSnapshot snapshots,
            List<IPlatformGoods> clients,
            IGoodsDetailCache cache,
            IGoodsLibrary library,
            ObjectMapper json,
            IGoodsGate gate) {
        this.snapshots = snapshots;
        this.clients = clients;
        this.cache = cache;
        this.library = library;
        this.json = json;
        this.gate = gate;
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
    public GoodsDetailDto detail(String userId, String goodsId, String platform, String platformGoodsId) {
        if (goodsId != null && !goodsId.isBlank()) {
            GoodsSnapshot found = snapshots.findById(goodsId)
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
                library.browse(userId, detail.goodsId(), detail.platform(), detail.platformGoodsId(), detail.title());
                return detail;
            }
        }
        GoodsSnapshot snapshot = snapshots.findByPlatform(platform, platformGoodsId)
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
                cards.add(toCard(snapshots.save(found)));
            }
            return cards;
        } catch (PlatformCallException ex) {
            throw upstream();
        }
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
            return snapshots.save(fetched);
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
        return gate.relationId(userId, platform.relationPlatform().code())
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
        library.browse(userId, snapshot.getId(), platformCode, snapshot.getPlatformGoodsId(), snapshot.getTitle());
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
 */

    static GoodsDetailDto toDetail(GoodsSnapshot snapshot) {
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

    private GoodsDetailDto read(String body) {
        try {
            return json.readValue(body, GoodsDetailDto.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
/**
 * 写入短缓存。写失败不影响本次详情返回。
 */

    private String write(GoodsDetailDto detail) {
        try {
            return json.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            return "";
        }
    }
}
