package com.omino.shopai.app.service.goods;

import java.util.Optional;
/**
 * 商品详情短缓存。过期再向上游要。
 */

public interface GoodsDetailCache {

    Optional<String> get(String platform, String platformGoodsId);

    void put(String platform, String platformGoodsId, String json);
}
