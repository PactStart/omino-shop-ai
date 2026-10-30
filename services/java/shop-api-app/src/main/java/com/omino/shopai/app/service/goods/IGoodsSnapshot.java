package com.omino.shopai.app.service.goods;

import com.omino.shopai.entity.GoodsSnapshot;

import java.util.Optional;
/**
 * 本次看过的商品快照，不是全量商品库。
 */

public interface IGoodsSnapshot {

    Optional<GoodsSnapshot> findById(String id);

    Optional<GoodsSnapshot> findByPlatform(String platform, String platformGoodsId);

    GoodsSnapshot save(GoodsSnapshot snapshot);
}
