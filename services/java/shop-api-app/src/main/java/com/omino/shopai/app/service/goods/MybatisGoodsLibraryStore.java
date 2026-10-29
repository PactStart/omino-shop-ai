package com.omino.shopai.app.service.goods;

import com.omino.shopai.enums.Platform;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.entity.GoodsCollection;
import com.omino.shopai.entity.GoodsCollectionItem;
import com.omino.shopai.entity.GoodsFavorite;
import com.omino.shopai.entity.GoodsSnapshot;
import com.omino.shopai.entity.UserEvent;
import com.omino.shopai.mapper.GoodsCollectionItemMapper;
import com.omino.shopai.mapper.GoodsCollectionMapper;
import com.omino.shopai.mapper.GoodsFavoriteMapper;
import com.omino.shopai.mapper.GoodsSnapshotMapper;
import com.omino.shopai.mapper.UserEventMapper;
import com.omino.shopai.support.Ids;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
/**
 * 持久化边界。事务和状态判断由调用方负责。
 */

@Repository
public class MybatisGoodsLibraryStore implements GoodsLibraryStore {

    private final UserEventMapper events;
    private final GoodsFavoriteMapper favorites;
    private final GoodsCollectionMapper collections;
    private final GoodsCollectionItemMapper items;
    private final GoodsSnapshotMapper snapshots;
/**
 * 浏览、收藏和合集的 MyBatis 访问。
 */

    public MybatisGoodsLibraryStore(
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
 * 记一次浏览。平台写成枚举，事件种类仍是字符串。
 */

    @Override
    public void browse(String userId, String goodsId, String platform, String platformGoodsId, String title) {
        UserEvent event = new UserEvent();
        event.setId(Ids.ulid());
        event.setUserId(userId);
        event.setKind("browse");
        event.setPlatform(Platform.fromCode(platform));
        event.setGoodsId(goodsId);
        event.setPlatformGoodsId(platformGoodsId);
        event.setTitle(title);
        event.setPayload("{}");
        event.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        events.insert(event);
    }
/**
 * 按时间倒序列出浏览，供回看。
 */

    @Override
    public SnapshotPage history(String userId, int page, int size) {
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
        return new SnapshotPage(list, safePage, safeSize, total == null ? 0 : total);
    }
/**
 * 写入或更新收藏。同一用户同一商品只留一行。
 */

    @Override
    public void favorite(String userId, String goodsId) {
        if (favoriteCount(userId, goodsId) > 0) {
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

    @Override
    public void unfavorite(String userId, String goodsId) {
        favorites.delete(new LambdaQueryWrapper<GoodsFavorite>()
                .eq(GoodsFavorite::getUserId, userId)
                .eq(GoodsFavorite::getGoodsId, goodsId));
    }
/**
 * 数出仍有效的收藏，供列表分页。
 */

    @Override
    public int favoriteCount(String userId, String goodsId) {
        Long count = favorites.selectCount(new LambdaQueryWrapper<GoodsFavorite>()
                .eq(GoodsFavorite::getUserId, userId)
                .eq(GoodsFavorite::getGoodsId, goodsId));
        return count == null ? 0 : count.intValue();
    }
/**
 * 列出收藏。已下架的不混进默认列表。
 */

    @Override
    public SnapshotPage favorites(String userId, int page, int size) {
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
        return new SnapshotPage(list, safePage, safeSize, total == null ? 0 : total);
    }

    @Override
    public Optional<GoodsCollection> collection(String id) {
        return Optional.ofNullable(collections.selectById(id));
    }
/**
 * 只取合集里尚未移除的商品。
 */

    @Override
    public List<GoodsSnapshot> activeItems(String collectionId) {
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
 */

    @Override
    public boolean markRemoved(String collectionId, String goodsId) {
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
}
