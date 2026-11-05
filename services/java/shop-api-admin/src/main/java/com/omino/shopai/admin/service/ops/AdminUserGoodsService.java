package com.omino.shopai.admin.service.ops;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.admin.support.AdminPages;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.dto.PageDto;
import com.omino.shopai.entity.AppInvite;
import com.omino.shopai.entity.AppUser;
import com.omino.shopai.entity.GoodsCollection;
import com.omino.shopai.entity.GoodsCollectionItem;
import com.omino.shopai.entity.GoodsFavorite;
import com.omino.shopai.entity.GoodsSnapshot;
import com.omino.shopai.entity.PlatformAuthorization;
import com.omino.shopai.entity.UserEvent;
import com.omino.shopai.enums.AuthStatus;
import com.omino.shopai.enums.Platform;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.AppInviteMapper;
import com.omino.shopai.mapper.AppUserMapper;
import com.omino.shopai.mapper.GoodsCollectionItemMapper;
import com.omino.shopai.mapper.GoodsCollectionMapper;
import com.omino.shopai.mapper.GoodsFavoriteMapper;
import com.omino.shopai.mapper.GoodsSnapshotMapper;
import com.omino.shopai.mapper.PlatformAuthorizationMapper;
import com.omino.shopai.mapper.UserEventMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户详情里的授权、商品和邀请记录只读。授权对象不含推广位。
 */
@Service
public class AdminUserGoodsService {

    private final AppUserMapper users;
    private final AppInviteMapper invites;
    private final PlatformAuthorizationMapper authorizations;
    private final UserEventMapper events;
    private final GoodsFavoriteMapper favorites;
    private final GoodsCollectionMapper collections;
    private final GoodsCollectionItemMapper items;
    private final GoodsSnapshotMapper snapshots;

    /**
     * 注入用户和商品相关的表。
     *
     * @param users 用户
     * @param authorizations 联盟授权
     * @param events 浏览事件
     * @param favorites 收藏
     * @param collections 合集
     * @param items 合集商品
     * @param snapshots 商品快照，用来补名称和主图
     * @param invites 邀请关系
     */
    public AdminUserGoodsService(
            AppUserMapper users,
            PlatformAuthorizationMapper authorizations,
            UserEventMapper events,
            GoodsFavoriteMapper favorites,
            GoodsCollectionMapper collections,
            GoodsCollectionItemMapper items,
            GoodsSnapshotMapper snapshots,
            AppInviteMapper invites) {
        this.users = users;
        this.invites = invites;
        this.authorizations = authorizations;
        this.events = events;
        this.favorites = favorites;
        this.collections = collections;
        this.items = items;
        this.snapshots = snapshots;
    }

    /**
     * 该用户邀请的人。手机脱敏。按邀请时间倒序。
     *
     * @param userId 邀请人主键
     * @param page 页码
     * @param size 页大小
     * @return 邀请记录页
     */
    public PageDto<Map<String, Object>> invites(String userId, Integer page, Integer size) {
        requireUser(userId);
        List<AppInvite> found = invites.selectList(new LambdaQueryWrapper<AppInvite>()
                .eq(AppInvite::getInviterId, userId)
                .orderByDesc(AppInvite::getCreatedAt));
        List<String> inviteeIds = new ArrayList<>();
        for (AppInvite invite : found) {
            if (invite.getInviteeId() != null && !inviteeIds.contains(invite.getInviteeId())) {
                inviteeIds.add(invite.getInviteeId());
            }
        }
        Map<String, AppUser> people = new LinkedHashMap<>();
        if (!inviteeIds.isEmpty()) {
            for (AppUser person : users.selectBatchIds(inviteeIds)) {
                people.put(person.getId(), person);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AppInvite invite : found) {
            AppUser person = people.get(invite.getInviteeId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("inviteeId", invite.getInviteeId());
            row.put("nickname", person == null ? null : person.getNickname());
            row.put("phone", AdminDirectory.mask(person == null ? null : person.getPhone()));
            row.put("status", invite.getStatus() == null ? null : invite.getStatus().code());
            row.put("validAt", invite.getValidAt());
            row.put("createdAt", invite.getCreatedAt());
            rows.add(row);
        }
        return AdminPages.of(rows, page, size);
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
     * @return 浏览页。名称优先用记录标题，图来自快照
     */
    public PageDto<Map<String, Object>> history(String userId, Integer page, Integer size) {
        requireUser(userId);
        List<UserEvent> rows = events.selectList(new LambdaQueryWrapper<UserEvent>()
                .eq(UserEvent::getUserId, userId)
                .eq(UserEvent::getKind, "browse")
                .orderByDesc(UserEvent::getCreatedAt));
        PageDto<UserEvent> slice = AdminPages.of(rows, page, size);
        Map<String, GoodsSnapshot> shots = shots(slice.list().stream().map(UserEvent::getGoodsId).toList());
        List<Map<String, Object>> cards = new ArrayList<>();
        for (UserEvent row : slice.list()) {
            cards.add(card(row.getGoodsId(), row.getTitle(), row.getCreatedAt(), shots.get(row.getGoodsId()), null));
        }
        return new PageDto<>(cards, slice.page(), slice.size(), slice.total());
    }

    /**
     * 收藏分页。
     *
     * @param userId 用户主键
     * @param page 页码
     * @param size 页大小
     * @return 收藏页。名称和图来自快照
     */
    public PageDto<Map<String, Object>> favorites(String userId, Integer page, Integer size) {
        requireUser(userId);
        List<GoodsFavorite> rows = favorites.selectList(new LambdaQueryWrapper<GoodsFavorite>()
                .eq(GoodsFavorite::getUserId, userId)
                .orderByDesc(GoodsFavorite::getCreatedAt));
        PageDto<GoodsFavorite> slice = AdminPages.of(rows, page, size);
        Map<String, GoodsSnapshot> shots = shots(slice.list().stream().map(GoodsFavorite::getGoodsId).toList());
        List<Map<String, Object>> cards = new ArrayList<>();
        for (GoodsFavorite row : slice.list()) {
            cards.add(card(row.getGoodsId(), null, row.getCreatedAt(), shots.get(row.getGoodsId()), null));
        }
        return new PageDto<>(cards, slice.page(), slice.size(), slice.total());
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
     * @return 商品行。名称和图来自快照
     */
    public List<Map<String, Object>> collectionItems(String userId, String collectionId) {
        requireUser(userId);
        GoodsCollection collection = collections.selectById(collectionId);
        if (collection == null || !userId.equals(collection.getUserId())) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "合集不存在");
        }
        List<GoodsCollectionItem> rows = items.selectList(new LambdaQueryWrapper<GoodsCollectionItem>()
                .eq(GoodsCollectionItem::getCollectionId, collectionId)
                .isNull(GoodsCollectionItem::getRemovedAt)
                .orderByAsc(GoodsCollectionItem::getSort));
        Map<String, GoodsSnapshot> shots = shots(rows.stream().map(GoodsCollectionItem::getGoodsId).toList());
        List<Map<String, Object>> cards = new ArrayList<>();
        for (GoodsCollectionItem row : rows) {
            cards.add(card(row.getGoodsId(), null, row.getCreatedAt(), shots.get(row.getGoodsId()), row.getSort()));
        }
        return cards;
    }

    /**
     * 按商品主键取出快照。没有快照的商品不在结果里。
     *
     * @param goodsIds 商品主键
     * @return 主键到快照
     */
    private Map<String, GoodsSnapshot> shots(List<String> goodsIds) {
        List<String> ids = new ArrayList<>();
        for (String goodsId : goodsIds) {
            if (goodsId != null && !goodsId.isBlank() && !ids.contains(goodsId)) {
                ids.add(goodsId);
            }
        }
        Map<String, GoodsSnapshot> found = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return found;
        }
        for (GoodsSnapshot row : snapshots.selectBatchIds(ids)) {
            found.put(row.getId(), row);
        }
        return found;
    }

    /**
     * 卡片用的名称、主图和时间。记录上的标题优先于快照标题。
     *
     * @param goodsId 商品主键
     * @param title 记录上的标题，可空
     * @param createdAt 时间
     * @param shot 商品快照，可空
     * @param sort 合集内排序，可空
     * @return 卡片字段
     */
    private static Map<String, Object> card(String goodsId, String title, OffsetDateTime createdAt, GoodsSnapshot shot, Integer sort) {
        Map<String, Object> row = new LinkedHashMap<>();
        String name = title == null || title.isBlank() ? null : title;
        if (name == null && shot != null && shot.getTitle() != null && !shot.getTitle().isBlank()) {
            name = shot.getTitle();
        }
        row.put("goodsId", goodsId);
        row.put("title", name);
        row.put("imageUrl", shot == null ? null : shot.getImageUrl());
        row.put("createdAt", createdAt);
        if (sort != null) {
            row.put("sort", sort);
        }
        return row;
    }

    private void requireUser(String userId) {
        AppUser user = users.selectById(userId);
        if (user == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "用户不存在");
        }
    }
}
