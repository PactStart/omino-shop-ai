package com.omino.shopai.admin.service.ops;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.admin.support.AdminPages;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.dto.PageDto;
import com.omino.shopai.entity.AppUser;
import com.omino.shopai.entity.GoodsCollection;
import com.omino.shopai.entity.GoodsCollectionItem;
import com.omino.shopai.entity.GoodsFavorite;
import com.omino.shopai.entity.PlatformAuthorization;
import com.omino.shopai.entity.UserEvent;
import com.omino.shopai.enums.AuthStatus;
import com.omino.shopai.enums.Platform;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.AppUserMapper;
import com.omino.shopai.mapper.GoodsCollectionItemMapper;
import com.omino.shopai.mapper.GoodsCollectionMapper;
import com.omino.shopai.mapper.GoodsFavoriteMapper;
import com.omino.shopai.mapper.PlatformAuthorizationMapper;
import com.omino.shopai.mapper.UserEventMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户详情里的授权和商品只读。授权对象不含推广位。
 */
@Service
public class AdminUserGoodsService {

    private final AppUserMapper users;
    private final PlatformAuthorizationMapper authorizations;
    private final UserEventMapper events;
    private final GoodsFavoriteMapper favorites;
    private final GoodsCollectionMapper collections;
    private final GoodsCollectionItemMapper items;

    /**
     * 注入用户和商品相关的表。
     *
     * @param users 用户
     * @param authorizations 联盟授权
     * @param events 浏览事件
     * @param favorites 收藏
     * @param collections 合集
     * @param items 合集商品
     */
    public AdminUserGoodsService(
            AppUserMapper users,
            PlatformAuthorizationMapper authorizations,
            UserEventMapper events,
            GoodsFavoriteMapper favorites,
            GoodsCollectionMapper collections,
            GoodsCollectionItemMapper items) {
        this.users = users;
        this.authorizations = authorizations;
        this.events = events;
        this.favorites = favorites;
        this.collections = collections;
        this.items = items;
    }

    /**
     * 四个平台的授权状态。没有记录视为未授权。不返回 relationId。
     *
     * @param userId 用户主键
     * @return 四行授权状态
     */
    public List<Map<String, String>> authorizations(String userId) {
        requireUser(userId);
        Map<Platform, AuthStatus> found = new LinkedHashMap<>();
        for (PlatformAuthorization row : authorizations.selectList(new LambdaQueryWrapper<PlatformAuthorization>()
                .eq(PlatformAuthorization::getUserId, userId))) {
            found.put(row.getPlatform(), row.getStatus());
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (Platform platform : Platform.values()) {
            AuthStatus status = found.getOrDefault(platform, AuthStatus.NONE);
            rows.add(Map.of("platform", platform.code(), "status", status == null ? AuthStatus.NONE.code() : status.code()));
        }
        return rows;
    }

    /**
     * 浏览记录，只取 kind=browse。
     *
     * @param userId 用户主键
     * @param page 页码
     * @param size 页大小
     * @return 浏览页
     */
    public PageDto<UserEvent> history(String userId, Integer page, Integer size) {
        requireUser(userId);
        List<UserEvent> rows = events.selectList(new LambdaQueryWrapper<UserEvent>()
                .eq(UserEvent::getUserId, userId)
                .eq(UserEvent::getKind, "browse")
                .orderByDesc(UserEvent::getCreatedAt));
        return AdminPages.of(rows, page, size);
    }

    /**
     * 收藏分页。
     *
     * @param userId 用户主键
     * @param page 页码
     * @param size 页大小
     * @return 收藏页
     */
    public PageDto<GoodsFavorite> favorites(String userId, Integer page, Integer size) {
        requireUser(userId);
        List<GoodsFavorite> rows = favorites.selectList(new LambdaQueryWrapper<GoodsFavorite>()
                .eq(GoodsFavorite::getUserId, userId)
                .orderByDesc(GoodsFavorite::getCreatedAt));
        return AdminPages.of(rows, page, size);
    }

    /**
     * 合集列表。
     *
     * @param userId 用户主键
     * @return 合集
     */
    public List<GoodsCollection> collections(String userId) {
        requireUser(userId);
        return collections.selectList(new LambdaQueryWrapper<GoodsCollection>().eq(GoodsCollection::getUserId, userId));
    }

    /**
     * 合集里尚未移除的商品。
     *
     * @param userId 用户主键
     * @param collectionId 合集主键
     * @return 商品行
     */
    public List<GoodsCollectionItem> collectionItems(String userId, String collectionId) {
        requireUser(userId);
        GoodsCollection collection = collections.selectById(collectionId);
        if (collection == null || !userId.equals(collection.getUserId())) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "合集不存在");
        }
        return items.selectList(new LambdaQueryWrapper<GoodsCollectionItem>()
                .eq(GoodsCollectionItem::getCollectionId, collectionId)
                .isNull(GoodsCollectionItem::getRemovedAt));
    }

    private void requireUser(String userId) {
        AppUser user = users.selectById(userId);
        if (user == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "用户不存在");
        }
    }
}
