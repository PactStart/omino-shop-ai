package com.omino.shopai.app.service.goods;

import com.omino.shopai.entity.GoodsCollection;
import com.omino.shopai.entity.GoodsSnapshot;

import java.util.List;
import java.util.Optional;
/**
 * 浏览、收藏和合集的存储端口。
 */

public interface GoodsLibraryStore {

    void browse(String userId, String goodsId, String platform, String platformGoodsId, String title);

    SnapshotPage history(String userId, int page, int size);

    void favorite(String userId, String goodsId);

    void unfavorite(String userId, String goodsId);

    int favoriteCount(String userId, String goodsId);

    SnapshotPage favorites(String userId, int page, int size);

    Optional<GoodsCollection> collection(String id);

    List<GoodsSnapshot> activeItems(String collectionId);

    boolean markRemoved(String collectionId, String goodsId);
/**
 * 一页商品快照。
 */

    record SnapshotPage(List<GoodsSnapshot> list, int page, int size, long total) {
    }
}
