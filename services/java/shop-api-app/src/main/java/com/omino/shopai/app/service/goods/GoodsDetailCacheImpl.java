package com.omino.shopai.app.service.goods;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
/**
 * 商品详情短缓存。过期后再向上游要。
 */

@Repository
public class GoodsDetailCacheImpl implements IGoodsDetailCache {

    static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;

    public GoodsDetailCacheImpl(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> get(String platform, String platformGoodsId) {
        return Optional.ofNullable(redis.opsForValue().get(key(platform, platformGoodsId)));
    }
/**
 * 短缓存详情。过期后重新向上游要，不把商品全量入库。
 */

    @Override
    public void put(String platform, String platformGoodsId, String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        redis.opsForValue().set(key(platform, platformGoodsId), json, TTL);
    }

    private static String key(String platform, String platformGoodsId) {
        return "goods:detail:" + platform + ":" + platformGoodsId;
    }
}
