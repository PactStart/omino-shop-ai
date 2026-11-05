package com.omino.shopai.app.goods;

import com.omino.shopai.dto.PageDto;

import com.omino.shopai.app.service.goods.IGoodsDetailCache;
import com.omino.shopai.entity.GoodsCollection;
import com.omino.shopai.entity.GoodsCollectionItem;
import com.omino.shopai.entity.GoodsSnapshot;
import com.omino.shopai.support.Ids;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MemoryGoods implements IGoodsDetailCache {

    private final Map<String, GoodsSnapshot> byId = new HashMap<>();
    private final Map<String, GoodsSnapshot> byPlatform = new HashMap<>();
    private final Map<String, String> cache = new HashMap<>();
    private final Map<String, String> authStatus = new HashMap<>();
    private final Map<String, String> relations = new HashMap<>();
    private final Map<String, Boolean> installs = new HashMap<>();
    private final List<String> clickUsers = new ArrayList<>();
    private final List<Browse> browses = new ArrayList<>();
    private final Map<String, String> favorites = new HashMap<>();
    private final Map<String, GoodsCollection> collections = new HashMap<>();
    private final List<GoodsCollectionItem> items = new ArrayList<>();

    /**
     * 给测试放一条授权和推广身份。
     *
     * @param userId 用户
     * @param platform 平台码
     * @param status 授权状态码
     * @param relationId 推广身份，可空
     */
    public void relation(String userId, String platform, String status, String relationId) {
        authStatus.put(userId + "\0" + platform, status);
        relations.put(userId + "\0" + platform, relationId);
    }

    GoodsCollection seedCollection(String userId, String title, GoodsSnapshot goods) {
        save(goods);
        GoodsCollection collection = new GoodsCollection();
        collection.setId(Ids.ulid());
        collection.setUserId(userId);
        collection.setTitle(title);
        collection.setSource("search");
        collection.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        collections.put(collection.getId(), collection);
        GoodsCollectionItem item = new GoodsCollectionItem();
        item.setId(Ids.ulid());
        item.setCollectionId(collection.getId());
        item.setGoodsId(goods.getId());
        item.setSort(0);
        item.setCreatedAt(collection.getCreatedAt());
        items.add(item);
        return collection;
    }

    public Optional<GoodsSnapshot> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<GoodsSnapshot> findByPlatform(String platform, String platformGoodsId) {
        return Optional.ofNullable(byPlatform.get(platform + "\0" + platformGoodsId));
    }

    public GoodsSnapshot save(GoodsSnapshot snapshot) {
        if (snapshot.getId() == null || snapshot.getId().isBlank()) {
            snapshot.setId(Ids.ulid());
        }
        byId.put(snapshot.getId(), snapshot);
        byPlatform.put(snapshot.getPlatform() + "\0" + snapshot.getPlatformGoodsId(), snapshot);
        return snapshot;
    }

    @Override
    public Optional<String> get(String platform, String platformGoodsId) {
        return Optional.ofNullable(cache.get(platform + "\0" + platformGoodsId));
    }

    @Override
    public void put(String platform, String platformGoodsId, String json) {
        if (json != null && !json.isBlank()) {
            cache.put(platform + "\0" + platformGoodsId, json);
        }
    }

    public boolean authorized(String userId, String platform) {
        return "authorized".equals(authStatus.get(userId + "\0" + platform));
    }

    public Optional<String> relationId(String userId, String platform) {
        String value = relations.get(userId + "\0" + platform);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    /**
     * 测试里的推广身份没有单独的缓存层，直接删掉这条。
     *
     * @param userId 用户
     * @param platform 平台码
     */
    public void forgetRelation(String userId, String platform) {
        relations.remove(userId + "\0" + platform);
    }

    public boolean installed(String userId, String platform) {
        return Boolean.TRUE.equals(installs.get(userId + "\0" + platform));
    }

    public void click(String userId, String platform, String goodsId, String platformGoodsId) {
        clickUsers.add(userId);
    }

    public int clicks(String userId) {
        return (int) clickUsers.stream().filter(userId::equals).count();
    }

    public void browse(String userId, String goodsId, String platform, String platformGoodsId, String title) {
        browses.add(0, new Browse(userId, goodsId));
    }

    public PageDto<GoodsSnapshot> history(String userId, int page, int size) {
        List<GoodsSnapshot> matched = browses.stream()
                .filter(row -> userId.equals(row.userId()))
                .map(row -> byId.get(row.goodsId()))
                .filter(snapshot -> snapshot != null)
                .toList();
        return slice(matched, page, size);
    }

    public void favorite(String userId, String goodsId) {
        favorites.putIfAbsent(userId + "\0" + goodsId, Ids.ulid());
    }

    public void unfavorite(String userId, String goodsId) {
        favorites.remove(userId + "\0" + goodsId);
    }

    public int favoriteCount(String userId, String goodsId) {
        return favorites.containsKey(userId + "\0" + goodsId) ? 1 : 0;
    }

    public PageDto<GoodsSnapshot> favorites(String userId, int page, int size) {
        List<GoodsSnapshot> matched = favorites.keySet().stream()
                .filter(key -> key.startsWith(userId + "\0"))
                .map(key -> byId.get(key.substring(userId.length() + 1)))
                .filter(snapshot -> snapshot != null)
                .toList();
        return slice(matched, page, size);
    }

    public Optional<GoodsCollection> collection(String id) {
        return Optional.ofNullable(collections.get(id));
    }

    public List<GoodsSnapshot> activeItems(String collectionId) {
        return items.stream()
                .filter(item -> collectionId.equals(item.getCollectionId()) && item.getRemovedAt() == null)
                .map(item -> byId.get(item.getGoodsId()))
                .filter(snapshot -> snapshot != null)
                .toList();
    }

    public boolean markRemoved(String collectionId, String goodsId) {
        for (GoodsCollectionItem item : items) {
            if (collectionId.equals(item.getCollectionId()) && goodsId.equals(item.getGoodsId())) {
                if (item.getRemovedAt() == null) {
                    item.setRemovedAt(OffsetDateTime.now(ZoneOffset.UTC));
                }
                return true;
            }
        }
        return false;
    }

    private static PageDto<GoodsSnapshot> slice(List<GoodsSnapshot> matched, int page, int size) {
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(matched.size(), from + size);
        List<GoodsSnapshot> pageItems = from >= matched.size() ? List.of() : List.copyOf(matched.subList(from, to));
        return new PageDto<GoodsSnapshot>(pageItems, page, size, matched.size());
    }

    private record Browse(String userId, String goodsId) {
    }
}
