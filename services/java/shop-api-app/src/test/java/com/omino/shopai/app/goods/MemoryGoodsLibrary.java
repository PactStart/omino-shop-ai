package com.omino.shopai.app.goods;

import com.omino.shopai.app.service.goods.GoodsLibraryServiceImpl;
import com.omino.shopai.dto.PageDto;
import com.omino.shopai.entity.GoodsCollection;
import com.omino.shopai.entity.GoodsSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * 用内存商品替换浏览、收藏和合集的表访问，归属校验仍走真实实现。
 */
public final class MemoryGoodsLibrary extends GoodsLibraryServiceImpl {

    private final MemoryGoods goods;

    /**
     * 绑定同一份内存商品。表访问全部转给它。
     *
     * @param goods 内存商品
     */
    public MemoryGoodsLibrary(MemoryGoods goods) {
        super(null, null, null, null, null);
        this.goods = goods;
    }

    /**
     * 从内存商品取出浏览页。
     *
     * @param userId 当前用户
     * @param page 页码，从 1 起
     * @param size 页大小
     * @return 快照页
     */
    @Override
    protected PageDto<GoodsSnapshot> loadHistory(String userId, int page, int size) {
        return goods.history(userId, page, size);
    }

    /**
     * 从内存商品取出收藏页。
     *
     * @param userId 当前用户
     * @param page 页码，从 1 起
     * @param size 页大小
     * @return 快照页
     */
    @Override
    protected PageDto<GoodsSnapshot> loadFavorites(String userId, int page, int size) {
        return goods.favorites(userId, page, size);
    }

    /**
     * 在内存商品里写入收藏。
     *
     * @param userId 当前用户
     * @param goodsId 商品号
     */
    @Override
    protected void storeFavorite(String userId, String goodsId) {
        goods.favorite(userId, goodsId);
    }

    /**
     * 从内存商品删掉收藏。
     *
     * @param userId 当前用户
     * @param goodsId 商品号
     */
    @Override
    protected void deleteFavorite(String userId, String goodsId) {
        goods.unfavorite(userId, goodsId);
    }

    /**
     * 数内存商品里的收藏。
     *
     * @param userId 当前用户
     * @param goodsId 商品号
     * @return 收藏行数
     */
    @Override
    protected int countFavorites(String userId, String goodsId) {
        return goods.favoriteCount(userId, goodsId);
    }

    /**
     * 从内存商品按主键读合集。
     *
     * @param id 合集编号
     * @return 合集；没有则空
     */
    @Override
    protected Optional<GoodsCollection> findCollection(String id) {
        return goods.collection(id);
    }

    /**
     * 从内存商品取出合集里尚未移除的商品。
     *
     * @param collectionId 合集编号
     * @return 仍有效的快照
     */
    @Override
    protected List<GoodsSnapshot> activeItems(String collectionId) {
        return goods.activeItems(collectionId);
    }

    /**
     * 在内存商品里把合集项标成已移除。
     *
     * @param collectionId 合集编号
     * @param goodsId 商品号
     * @return 找到这一行时为真
     */
    @Override
    protected boolean markRemoved(String collectionId, String goodsId) {
        return goods.markRemoved(collectionId, goodsId);
    }

    /**
     * 从内存商品按主键读快照。
     *
     * @param id 商品号
     * @return 快照；没有则空
     */
    @Override
    protected Optional<GoodsSnapshot> findSnapshot(String id) {
        return goods.findById(id);
    }
}
