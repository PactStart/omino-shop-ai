package com.omino.shopai.app.service.goods;

import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.GoodsCollection;
import com.omino.shopai.entity.GoodsSnapshot;
import org.springframework.stereotype.Service;

import java.util.List;
/**
 * GoodsLibraryService 编排用例，事务和校验放在这里。
 */

@Service
public class GoodsLibraryService {

    private final GoodsSnapshotStore snapshots;
    private final GoodsLibraryStore library;
/**
 * 浏览、收藏和合集的用例。不在这里转链。
 */

    public GoodsLibraryService(GoodsSnapshotStore snapshots, GoodsLibraryStore library) {
        this.snapshots = snapshots;
        this.library = library;
    }

    public GoodsPage<GoodsCard> history(String userId, int page, int size) {
        return cards(library.history(userId, pageOf(page), sizeOf(size)));
    }

    public GoodsPage<GoodsCard> favorites(String userId, int page, int size) {
        return cards(library.favorites(userId, pageOf(page), sizeOf(size)));
    }
/**
 * 收藏前确认商品属于当前用户看过的快照。
 */

    public void favorite(String userId, String goodsId) {
        if (goodsId == null || goodsId.isBlank() || snapshots.findById(goodsId).isEmpty()) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "商品不存在");
        }
        library.favorite(userId, goodsId);
    }

    public void unfavorite(String userId, String goodsId) {
        library.unfavorite(userId, goodsId);
    }

    public int favoriteCount(String userId, String goodsId) {
        return library.favoriteCount(userId, goodsId);
    }
/**
 * 读出合集及其中仍有效的商品。
 */

    public CollectionView collection(String userId, String collectionId) {
        GoodsCollection collection = owned(userId, collectionId);
        List<GoodsCard> goods = library.activeItems(collectionId).stream().map(GoodsQueryService::toCard).toList();
        return new CollectionView(collection.getId(), collection.getTitle(), goods);
    }
/**
 * 移除。
 */

    public void removeCollectionItem(String userId, String collectionId, String goodsId) {
        owned(userId, collectionId);
        if (!library.markRemoved(collectionId, goodsId)) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "商品不在合集中");
        }
    }
/**
 * 合集或收藏必须属于当前用户，否则当不存在。
 */

    private GoodsCollection owned(String userId, String collectionId) {
        GoodsCollection collection = library.collection(collectionId)
                .orElseThrow(() -> new ApiBusinessException(ErrorCodes.NOT_FOUND, "合集不存在"));
        if (!userId.equals(collection.getUserId())) {
            throw new ApiBusinessException(ErrorCodes.FORBIDDEN, "无权操作");
        }
        return collection;
    }

    private static GoodsPage<GoodsCard> cards(GoodsLibraryStore.SnapshotPage page) {
        return new GoodsPage<>(
                page.list().stream().map(GoodsQueryService::toCard).toList(),
                page.page(),
                page.size(),
                page.total());
    }

    private static int pageOf(int page) {
        return page < 1 ? 1 : page;
    }

    private static int sizeOf(int size) {
        return size < 1 || size > 100 ? 20 : size;
    }
}
