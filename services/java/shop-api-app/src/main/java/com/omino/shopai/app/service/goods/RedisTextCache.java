package com.omino.shopai.app.service.goods;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis 上的短字符串。空值不写入。
 */
final class RedisTextCache implements TextCache {

    private final StringRedisTemplate redis;

    RedisTextCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        if (value == null || value.isBlank()) {
            return;
        }
        redis.opsForValue().set(key, value, ttl);
    }
}
