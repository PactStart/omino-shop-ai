package com.omino.shopai.app.service.goods;

import java.util.Optional;

/**
 * 商品详情短缓存。过期再向上游要。
 */
public interface IGoodsDetailCache {

    /**
     * 读一条详情缓存。
     *
     * @param platform 平台码
     * @param platformGoodsId 平台商品号
     * @return 缓存里的 JSON；没有则空
     */
    Optional<String> get(String platform, String platformGoodsId);

    /**
     * 写入一条详情缓存。
     *
     * @param platform 平台码
     * @param platformGoodsId 平台商品号
     * @param json 详情 JSON
     */
    void put(String platform, String platformGoodsId, String json);
}
