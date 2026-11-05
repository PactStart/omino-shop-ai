package com.omino.shopai.app.service.goods;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.dto.CollectionViewDto;
import com.omino.shopai.app.dto.GoodsCardDto;
import com.omino.shopai.dto.PageDto;
import com.omino.shopai.entity.GoodsCollection;
import com.omino.shopai.entity.GoodsCollectionItem;
import com.omino.shopai.entity.GoodsFavorite;
import com.omino.shopai.entity.GoodsSnapshot;
import com.omino.shopai.entity.UserEvent;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.GoodsCollectionItemMapper;
import com.omino.shopai.mapper.GoodsCollectionMapper;
import com.omino.shopai.mapper.GoodsFavoriteMapper;
import com.omino.shopai.mapper.GoodsSnapshotMapper;
import com.omino.shopai.mapper.UserEventMapper;
import com.omino.shopai.support.Ids;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 浏览、收藏和合集。不在这里转链。
 */
@Service
public class GoodsLibraryServiceImpl implements IGoodsLibraryService {

    private final UserEventMapper events;
    private final GoodsFavoriteMapper favorites;
    private final GoodsCollectionMapper collections;
    private final GoodsCollectionItemMapper items;
    private final GoodsSnapshotMapper snapshots;

    /**
     * 浏览、收藏和合集各自读自己的表。快照只用来确认商品还在。
     *
     * @param events 浏览事件表
     * @param favorites 收藏表
     * @param collections 合集表
     * @param items 合集商品表
     * @param snapshots 商品快照表
     */
    public GoodsLibraryServiceImpl(
            UserEventMapper events,
            GoodsFavoriteMapper favorites,
            GoodsCollectionMapper collections,
            GoodsCollectionItemMapper items,
            GoodsSnapshotMapper snapshots) {
        this.events = events;
        this.favorites = favorites;
        this.collections = collections;
        this.items = items;
        this.snapshots = snapshots;
    }

    /**
     * 按时间倒序列出当前用户看过的商品。
     *
     * @param userId 当前用户
     * @param page 页码，从 1 起
     * @param size 页大小，超出范围时按 20
     * @return 商品卡页
     */
    @Override
    public PageDto<GoodsCardDto> history(String userId, int page, int size) {
        return cards(loadHistory(userId, pageOf(page), sizeOf(size)));
    }

    /**
     * 列出当前用户的收藏。
     *
     * @param userId 当前用户
     * @param page 页码，从 1 起
     * @param size 页大小，超出范围时按 20
     * @return 商品卡页
     */
    @Override
    public PageDto<GoodsCardDto> favorites(String userId, int page, int size) {
        return cards(loadFavorites(userId, pageOf(page), sizeOf(size)));
    }

    /**
     * 收藏前确认商品属于当前用户看过的快照。
     *
     * @param userId 当前用户
     * @param goodsId 商品号
     */
    @Override
    public void favorite(String userId, String goodsId) {
        if (goodsId == null || goodsId.isBlank() || findSnapshot(goodsId).isEmpty()) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "商品不存在");
        }
        storeFavorite(userId, goodsId);
    }

    /**
     * 取消收藏。本来就没有也按成功处理。
     *
     * @param userId 当前用户
     * @param goodsId 商品号
     */
    @Override
    public void unfavorite(String userId, String goodsId) {
        deleteFavorite(userId, goodsId);
    }

    /**
     * 数出该用户对这件商品仍有效的收藏。
     *
     * @param userId 当前用户
     * @param goodsId 商品号
     * @return 收藏行数
     */
    @Override
    public int favoriteCount(String userId, String goodsId) {
        return countFavorites(userId, goodsId);
    }

    /**
     * 读出合集及其中仍有效的商品。不是本人则拒绝。
     *
     * @param userId 当前用户
     * @param collectionId 合集编号
     * @return 合集和商品卡
     */
    @Override
    public CollectionViewDto collection(String userId, String collectionId) {
        GoodsCollection collection = owned(userId, collectionId);
        List<GoodsCardDto> goods = activeItems(collectionId).stream().map(GoodsServiceImpl::toCard).toList();
        return new CollectionViewDto(collection.getId(), collection.getTitle(), goods);
    }

    /**
     * 从合集里标掉一件商品。不是本人则拒绝。
     *
     * @param userId 当前用户
     * @param collectionId 合集编号
     * @param goodsId 商品号
     */
    @Override
    public void removeCollectionItem(String userId, String collectionId, String goodsId) {
        owned(userId, collectionId);
        if (!markRemoved(collectionId, goodsId)) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "商品不在合集中");
        }
    }

    /**
     * 合集必须属于当前用户，否则当不存在。
     *
     * @param userId 当前用户
     * @param collectionId 合集编号
     * @return 合集
     */
    private GoodsCollection owned(String userId, String collectionId) {
        GoodsCollection collection = findCollection(collectionId)
                .orElseThrow(() -> new ApiBusinessException(ErrorCodes.NOT_FOUND, "合集不存在"));
        if (!userId.equals(collection.getUserId())) {
            throw new ApiBusinessException(ErrorCodes.FORBIDDEN, "无权操作");
        }
        return collection;
    }

    /**
     * 把快照页收成商品卡页。页码和总数保持原样。
     *
     * @param page 快照页
     * @return 商品卡页
     */
    private static PageDto<GoodsCardDto> cards(PageDto<GoodsSnapshot> page) {
        return new PageDto<>(
                page.list().stream().map(GoodsServiceImpl::toCard).toList(),
                page.page(),
                page.size(),
                page.total());
    }

    /**
     * 页码小于 1 时从第一页开始。
     *
     * @param page 请求页码
     * @return 至少为 1 的页码
     */
    private static int pageOf(int page) {
        return page < 1 ? 1 : page;
    }

    /**
     * 页大小超出 1 到 100 时用 20。
     *
     * @param size 请求页大小
     * @return 收进范围内的页大小
     */
    private static int sizeOf(int size) {
        return size < 1 || size > 100 ? 20 : size;
    }

    /**
     * 按时间倒序列出浏览，供回看。
     *
     * @param userId 当前用户
     * @param page 页码，从 1 起
     * @param size 页大小
     * @return 快照页
     */
    protected PageDto<GoodsSnapshot> loadHistory(String userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        LambdaQueryWrapper<UserEvent> filter = new LambdaQueryWrapper<UserEvent>()
                .eq(UserEvent::getUserId, userId)
                .eq(UserEvent::getKind, "browse");
        Long total = events.selectCount(filter);
        List<UserEvent> rows = events.selectList(new LambdaQueryWrapper<UserEvent>()
                .eq(UserEvent::getUserId, userId)
                .eq(UserEvent::getKind, "browse")
                .orderByDesc(UserEvent::getCreatedAt)
                .last("LIMIT " + safeSize + " OFFSET " + ((long) (safePage - 1) * safeSize)));
        List<GoodsSnapshot> list = new ArrayList<>();
        for (UserEvent event : rows) {
            GoodsSnapshot snapshot = snapshots.selectById(event.getGoodsId());
            if (snapshot != null) {
                list.add(snapshot);
            }
        }
        return new PageDto<GoodsSnapshot>(list, safePage, safeSize, total == null ? 0 : total);
    }

    /**
     * 写入或更新收藏。同一用户同一商品只留一行。
     *
     * @param userId 当前用户
     * @param goodsId 商品号
     */
    protected void storeFavorite(String userId, String goodsId) {
        if (countFavorites(userId, goodsId) > 0) {
            return;
        }
        GoodsFavorite row = new GoodsFavorite();
        row.setId(Ids.ulid());
        row.setUserId(userId);
        row.setGoodsId(goodsId);
        row.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            favorites.insert(row);
        } catch (DuplicateKeyException ignored) {
            // 同一用户同一商品再藏一次，保持原行。
        }
    }

    /**
     * 删掉该用户对这件商品的收藏。
     *
     * @param userId 当前用户
     * @param goodsId 商品号
     */
    protected void deleteFavorite(String userId, String goodsId) {
        favorites.delete(new LambdaQueryWrapper<GoodsFavorite>()
                .eq(GoodsFavorite::getUserId, userId)
                .eq(GoodsFavorite::getGoodsId, goodsId));
    }

    /**
     * 数出仍有效的收藏。
     *
     * @param userId 当前用户
     * @param goodsId 商品号
     * @return 收藏行数
     */
    protected int countFavorites(String userId, String goodsId) {
        Long count = favorites.selectCount(new LambdaQueryWrapper<GoodsFavorite>()
                .eq(GoodsFavorite::getUserId, userId)
                .eq(GoodsFavorite::getGoodsId, goodsId));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 列出收藏。已下架的不混进默认列表。
     *
     * @param userId 当前用户
     * @param page 页码，从 1 起
     * @param size 页大小
     * @return 快照页
     */
    protected PageDto<GoodsSnapshot> loadFavorites(String userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        Long total = favorites.selectCount(new LambdaQueryWrapper<GoodsFavorite>()
                .eq(GoodsFavorite::getUserId, userId));
        List<GoodsFavorite> rows = favorites.selectList(new LambdaQueryWrapper<GoodsFavorite>()
                .eq(GoodsFavorite::getUserId, userId)
                .orderByDesc(GoodsFavorite::getCreatedAt)
                .last("LIMIT " + safeSize + " OFFSET " + ((long) (safePage - 1) * safeSize)));
        List<GoodsSnapshot> list = new ArrayList<>();
        for (GoodsFavorite favorite : rows) {
            GoodsSnapshot snapshot = snapshots.selectById(favorite.getGoodsId());
            if (snapshot != null) {
                list.add(snapshot);
            }
        }
        return new PageDto<GoodsSnapshot>(list, safePage, safeSize, total == null ? 0 : total);
    }

    /**
     * 按主键读合集。
     *
     * @param id 合集编号
     * @return 合集；没有则空
     */
    protected Optional<GoodsCollection> findCollection(String id) {
        return Optional.ofNullable(collections.selectById(id));
    }

    /**
     * 只取合集里尚未移除的商品。
     *
     * @param collectionId 合集编号
     * @return 仍有效的快照
     */
    protected List<GoodsSnapshot> activeItems(String collectionId) {
        List<GoodsCollectionItem> rows = items.selectList(new LambdaQueryWrapper<GoodsCollectionItem>()
                .eq(GoodsCollectionItem::getCollectionId, collectionId)
                .isNull(GoodsCollectionItem::getRemovedAt)
                .orderByAsc(GoodsCollectionItem::getSort));
        List<GoodsSnapshot> list = new ArrayList<>();
        for (GoodsCollectionItem row : rows) {
            GoodsSnapshot snapshot = snapshots.selectById(row.getGoodsId());
            if (snapshot != null) {
                list.add(snapshot);
            }
        }
        return list;
    }

    /**
     * 把合集项标成已移除，不物理删行。
     *
     * @param collectionId 合集编号
     * @param goodsId 商品号
     * @return 找到这一行时为真
     */
    protected boolean markRemoved(String collectionId, String goodsId) {
        GoodsCollectionItem row = items.selectOne(new LambdaQueryWrapper<GoodsCollectionItem>()
                .eq(GoodsCollectionItem::getCollectionId, collectionId)
                .eq(GoodsCollectionItem::getGoodsId, goodsId));
        if (row == null) {
            return false;
        }
        if (row.getRemovedAt() == null) {
            row.setRemovedAt(OffsetDateTime.now(ZoneOffset.UTC));
            items.updateById(row);
        }
        return true;
    }

    /**
     * 按主键读快照，用来确认收藏的商品还在。
     *
     * @param id 商品号
     * @return 快照；没有则空
     */
    protected Optional<GoodsSnapshot> findSnapshot(String id) {
        return Optional.ofNullable(snapshots.selectById(id));
    }
}
