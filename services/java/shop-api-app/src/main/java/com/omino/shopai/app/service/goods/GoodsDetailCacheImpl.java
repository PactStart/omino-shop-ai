package com.omino.shopai.app.service.goods;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 商品详情短缓存。过期后再向上游要。
 */
@Component
public class GoodsDetailCacheImpl implements IGoodsDetailCache {

    static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;

    /**
     * 详情 JSON 放在 Redis，十分钟后过期。
     *
     * @param redis 字符串缓存
     */
    public GoodsDetailCacheImpl(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 读一条详情缓存。
     *
     * @param platform 平台码
     * @param platformGoodsId 平台商品号
     * @return 缓存里的 JSON；没有则空
     */
    @Override
    public Optional<String> get(String platform, String platformGoodsId) {
        return Optional.ofNullable(redis.opsForValue().get(key(platform, platformGoodsId)));
    }

    /**
     * 短缓存详情。过期后重新向上游要，不把商品全量入库。
     *
     * @param platform 平台码
     * @param platformGoodsId 平台商品号
     * @param json 详情 JSON；空白则不写
     */
    @Override
    public void put(String platform, String platformGoodsId, String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        redis.opsForValue().set(key(platform, platformGoodsId), json, TTL);
    }

    /**
     * 拼详情缓存的键。
     *
     * @param platform 平台码
     * @param platformGoodsId 平台商品号
     * @return Redis 键
     */
    private static String key(String platform, String platformGoodsId) {
        return "goods:detail:" + platform + ":" + platformGoodsId;
    }
}
