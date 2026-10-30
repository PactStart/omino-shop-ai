package com.omino.shopai.app.service.goods;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 推广身份先读 Redis。未命中再查授权表，查到才写入缓存，避免把「还没有」卡住。
 */
@Component
public class CachingUnionRelation implements UnionRelation {

    static final Duration TTL = Duration.ofMinutes(10);

    private final TextCache cache;
    private final RelationLookup lookup;

    @Autowired
    public CachingUnionRelation(StringRedisTemplate redis, GoodsGateStore gate) {
        this(new RedisTextCache(redis), gate::relationId);
    }

    public CachingUnionRelation(TextCache cache, RelationLookup lookup) {
        this.cache = cache;
        this.lookup = lookup;
    }

    @Override
    public Optional<String> find(String userId, String platform) {
        if (userId == null || userId.isBlank() || platform == null || platform.isBlank()) {
            return Optional.empty();
        }
        String key = key(userId, platform);
        String cached = cache.get(key);
        if (cached != null && !cached.isBlank()) {
            return Optional.of(cached);
        }
        Optional<String> found = lookup.relationId(userId, platform).filter(id -> !id.isBlank());
        found.ifPresent(id -> cache.put(key, id, TTL));
        return found;
    }

    static String key(String userId, String platform) {
        return "union:relation:" + userId + ":" + platform;
    }
}
