package com.omino.shopai.app.service.goods;

import com.omino.shopai.app.dto.CollectionViewDto;
import com.omino.shopai.app.dto.GoodsCardDto;
import com.omino.shopai.dto.PageDto;

/**
 * 浏览、收藏和合集。不在这里转链。
 */
public interface IGoodsLibraryService {

    /**
     * 按时间倒序列出当前用户看过的商品。
     *
     * @param userId 当前用户
     * @param page 页码，从 1 起
     * @param size 页大小，超出范围时按 20
     * @return 商品卡页
     */
    PageDto<GoodsCardDto> history(String userId, int page, int size);

    /**
     * 列出当前用户的收藏。
     *
     * @param userId 当前用户
     * @param page 页码，从 1 起
     * @param size 页大小，超出范围时按 20
     * @return 商品卡页
     */
    PageDto<GoodsCardDto> favorites(String userId, int page, int size);

    /**
     * 收藏前确认商品属于当前用户看过的快照。
     *
     * @param userId 当前用户
     * @param goodsId 商品号
     */
    void favorite(String userId, String goodsId);

    /**
     * 取消收藏。本来就没有也按成功处理。
     *
     * @param userId 当前用户
     * @param goodsId 商品号
     */
    void unfavorite(String userId, String goodsId);

    /**
     * 数出该用户对这件商品仍有效的收藏。
     *
     * @param userId 当前用户
     * @param goodsId 商品号
     * @return 收藏行数
     */
    int favoriteCount(String userId, String goodsId);

    /**
     * 读出合集及其中仍有效的商品。不是本人则拒绝。
     *
     * @param userId 当前用户
     * @param collectionId 合集编号
     * @return 合集和商品卡
     */
    CollectionViewDto collection(String userId, String collectionId);

    /**
     * 从合集里标掉一件商品。不是本人则拒绝。
     *
     * @param userId 当前用户
     * @param collectionId 合集编号
     * @param goodsId 商品号
     */
    void removeCollectionItem(String userId, String collectionId, String goodsId);
}
