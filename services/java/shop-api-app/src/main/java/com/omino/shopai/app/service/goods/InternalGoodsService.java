package com.omino.shopai.app.service.goods;

import com.omino.shopai.app.dto.GoodsCardDto;

import com.omino.shopai.app.dto.GoodsDetailDto;

import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.enums.Platform;
import com.omino.shopai.entity.GoodsCollection;
import com.omino.shopai.entity.GoodsCollectionItem;
import com.omino.shopai.mapper.GoodsCollectionItemMapper;
import com.omino.shopai.mapper.GoodsCollectionMapper;
import com.omino.shopai.support.Ids;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * InternalGoodsService 编排用例，事务和校验放在这里。
 */

@Service
public class InternalGoodsService {

    private final IGoodsService goods;
    private final GoodsCollectionMapper collections;
    private final GoodsCollectionItemMapper items;

    /**
     * 给 Agent 工具用的搜品入口。联盟调用仍走适配器。
     *
     * @param goods
     * @param collections
     * @param items
     */
    public InternalGoodsService(IGoodsService goods, GoodsCollectionMapper collections, GoodsCollectionItemMapper items) {
        this.goods = goods;
        this.collections = collections;
        this.items = items;
    }

    public Map<String, Object> search(String userId, String platform, String keyword, List<String> keywords, List<String> platforms) {
        String query = keyword;
        if ((query == null || query.isBlank()) && keywords != null && !keywords.isEmpty()) {
            query = keywords.get(0);
        }
        List<String> targets = platforms == null || platforms.isEmpty()
                ? (platform == null || platform.isBlank()
                        ? Arrays.stream(Platform.values()).map(Platform::code).toList()
                        : List.of(platform))
                : platforms;
        List<GoodsCardDto> cards = new ArrayList<>();
        for (String target : targets) {
            cards.addAll(goods.search(userId, target, query));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", cards.size());
        if (cards.size() > 6) {
            body.put("collectionId", persist(userId, query, cards));
            body.put("goods", cards.subList(0, 4));
        } else {
            body.put("goods", cards);
        }
        return body;
    }
    /**
     * 从链接里认出平台和商品号，再走详情。
     *
     * @param userId 当前用户
     * @param sourceUrl 商品链接
     * @return 商品详情
     */
    public GoodsDetailDto parseLink(String userId, String sourceUrl) {
        Platform platform = platformOf(sourceUrl);
        String goodsId = digits(sourceUrl);
        if (platform == null || goodsId == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "未能识别商品");
        }
        return goods.detail(userId, null, platform.code(), goodsId);
    }

    public GoodsDetailDto parseImage(String imageObjectKey) {
        throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "未能识别商品");
    }

    public GoodsDetailDto detail(String userId, String goodsId, String platform, String platformGoodsId) {
        return goods.detail(userId, goodsId, platform, platformGoodsId);
    }
/**
 * 条数超过一屏时落成合集，对话里只推合集卡。
 */

    private String persist(String userId, String title, List<GoodsCardDto> cards) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        GoodsCollection collection = new GoodsCollection();
        collection.setId(Ids.ulid());
        collection.setUserId(userId);
        collection.setTitle(title);
        collection.setSource("search");
        collection.setCreatedAt(now);
        collections.insert(collection);
        int sort = 0;
        for (GoodsCardDto card : cards) {
            GoodsCollectionItem item = new GoodsCollectionItem();
            item.setId(Ids.ulid());
            item.setCollectionId(collection.getId());
            item.setGoodsId(card.goodsId());
            item.setSort(sort++);
            item.setCreatedAt(now);
            items.insert(item);
        }
        return collection.getId();
    }
    /**
     * 从商品号前缀认出平台。认不出则空，避免打错联盟。
     *
     * @param url 商品链接
     * @return 平台；认不出则为空
     */
    private static Platform platformOf(String url) {
        if (url == null) {
            return null;
        }
        String lower = url.toLowerCase();
        if (lower.contains("taobao.") || lower.contains("tmall.")) {
            return Platform.TAOBAO;
        }
        if (lower.contains("jd.com") || lower.contains("jd.hk")) {
            return Platform.JD;
        }
        if (lower.contains("yangkeduo.") || lower.contains("pinduoduo.")) {
            return Platform.PDD;
        }
        if (lower.contains("vip.com")) {
            return Platform.VIP;
        }
        return null;
    }

    /**
     * 只保留数字，供和平台商品号对齐。
     * @param url
     * @return
     */
    private static String digits(String url) {
        if (url == null) {
            return null;
        }
        StringBuilder found = new StringBuilder();
        for (int i = 0; i < url.length(); i++) {
            char ch = url.charAt(i);
            if (ch >= '0' && ch <= '9') {
                found.append(ch);
            } else if (found.length() >= 6) {
                return found.toString();
            } else {
                found.setLength(0);
            }
        }
        return found.length() >= 6 ? found.toString() : null;
    }
}
