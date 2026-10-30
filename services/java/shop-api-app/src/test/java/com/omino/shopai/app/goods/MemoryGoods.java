package com.omino.shopai.app.goods;

import com.omino.shopai.app.service.goods.GoodsDetailCache;
import com.omino.shopai.app.service.goods.GoodsGateStore;
import com.omino.shopai.app.service.goods.GoodsLibraryStore;
import com.omino.shopai.app.service.goods.GoodsSnapshotStore;
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

public final class MemoryGoods implements GoodsSnapshotStore, GoodsDetailCache, GoodsGateStore, GoodsLibraryStore {

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

    void relation(String userId, String platform, String status, String relationId) {
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

    @Override
    public Optional<GoodsSnapshot> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<GoodsSnapshot> findByPlatform(String platform, String platformGoodsId) {
        return Optional.ofNullable(byPlatform.get(platform + "\0" + platformGoodsId));
    }

    @Override
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

    @Override
    public boolean authorized(String userId, String platform) {
        return "authorized".equals(authStatus.get(userId + "\0" + platform));
    }

    @Override
    public Optional<String> relationId(String userId, String platform) {
        String value = relations.get(userId + "\0" + platform);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    @Override
    public boolean installed(String userId, String platform) {
        return Boolean.TRUE.equals(installs.get(userId + "\0" + platform));
    }

    @Override
    public void click(String userId, String platform, String goodsId, String platformGoodsId) {
        clickUsers.add(userId);
    }

    @Override
    public int clicks(String userId) {
        return (int) clickUsers.stream().filter(userId::equals).count();
    }

    @Override
    public void browse(String userId, String goodsId, String platform, String platformGoodsId, String title) {
        browses.add(0, new Browse(userId, goodsId));
    }

    @Override
    public SnapshotPage history(String userId, int page, int size) {
        List<GoodsSnapshot> matched = browses.stream()
                .filter(row -> userId.equals(row.userId()))
                .map(row -> byId.get(row.goodsId()))
                .filter(snapshot -> snapshot != null)
                .toList();
        return slice(matched, page, size);
    }

    @Override
    public void favorite(String userId, String goodsId) {
        favorites.putIfAbsent(userId + "\0" + goodsId, Ids.ulid());
    }

    @Override
    public void unfavorite(String userId, String goodsId) {
        favorites.remove(userId + "\0" + goodsId);
    }

    @Override
    public int favoriteCount(String userId, String goodsId) {
        return favorites.containsKey(userId + "\0" + goodsId) ? 1 : 0;
    }

    @Override
    public SnapshotPage favorites(String userId, int page, int size) {
        List<GoodsSnapshot> matched = favorites.keySet().stream()
                .filter(key -> key.startsWith(userId + "\0"))
                .map(key -> byId.get(key.substring(userId.length() + 1)))
                .filter(snapshot -> snapshot != null)
                .toList();
        return slice(matched, page, size);
    }

    @Override
    public Optional<GoodsCollection> collection(String id) {
        return Optional.ofNullable(collections.get(id));
    }

    @Override
    public List<GoodsSnapshot> activeItems(String collectionId) {
        return items.stream()
                .filter(item -> collectionId.equals(item.getCollectionId()) && item.getRemovedAt() == null)
                .map(item -> byId.get(item.getGoodsId()))
                .filter(snapshot -> snapshot != null)
                .toList();
    }

    @Override
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

    private static SnapshotPage slice(List<GoodsSnapshot> matched, int page, int size) {
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(matched.size(), from + size);
        List<GoodsSnapshot> pageItems = from >= matched.size() ? List.of() : List.copyOf(matched.subList(from, to));
        return new SnapshotPage(pageItems, page, size, matched.size());
    }

    private record Browse(String userId, String goodsId) {
    }
}
