package com.omino.shopai.app.service.goods;

import com.omino.shopai.enums.Platform;
import com.omino.shopai.enums.AuthStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.entity.AppUserInstalledApp;
import com.omino.shopai.entity.ClickEvent;
import com.omino.shopai.entity.PlatformAuthorization;
import com.omino.shopai.mapper.AppUserInstalledAppMapper;
import com.omino.shopai.mapper.ClickEventMapper;
import com.omino.shopai.mapper.PlatformAuthorizationMapper;
import com.omino.shopai.support.Ids;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
/**
 * 持久化边界。事务和状态判断由调用方负责。
 */

@Repository
public class GoodsGateImpl implements IGoodsGate {

    private static final Duration RELATION_TTL = Duration.ofMinutes(10);

    private final PlatformAuthorizationMapper authorizations;
    private final AppUserInstalledAppMapper installedApps;
    private final ClickEventMapper clicks;
    private final StringRedisTemplate redis;

    /**
     * 联盟授权、安装态和点击记录。推广身份先读 Redis，未命中再查表。
     *
     * @param authorizations 授权表
     * @param installedApps 已安装 APP
     * @param clicks 点击记录
     * @param redis 推广身份缓存
     */
    public GoodsGateImpl(
            PlatformAuthorizationMapper authorizations,
            AppUserInstalledAppMapper installedApps,
            ClickEventMapper clicks,
            StringRedisTemplate redis) {
        this.authorizations = authorizations;
        this.installedApps = installedApps;
        this.clicks = clicks;
        this.redis = redis;
    }

    /**
     * 该用户是否已授权这个平台。
     *
     * @param userId 当前用户
     * @param platform 平台码
     * @return 状态为已授权时为真
     */
    @Override
    public boolean authorized(String userId, String platform) {
        Platform parsed = platformOf(platform);
        if (parsed == null) {
            return false;
        }
        PlatformAuthorization row = authorizations.selectOne(new LambdaQueryWrapper<PlatformAuthorization>()
                .eq(PlatformAuthorization::getUserId, userId)
                .eq(PlatformAuthorization::getPlatform, parsed));
        return row != null && AuthStatus.AUTHORIZED == row.getStatus();
    }

    /**
     * 当前用户在该平台的推广身份。先读缓存，没有再查表，查到才写入，避免把「还没有」卡住十分钟。
     *
     * @param userId 当前用户
     * @param platform 平台码。拼多多的调用方应传入京东
     * @return 非空的 relation_id；没有则空
     */
    @Override
    public Optional<String> relationId(String userId, String platform) {
        return cached(
                userId,
                platform,
                key -> redis.opsForValue().get(key),
                (key, value) -> redis.opsForValue().set(key, value, RELATION_TTL),
                this::loadRelation);
    }

    /**
     * 授权变更后丢掉缓存。下一次转链会重新读表。
     *
     * @param userId 当前用户
     * @param platform 平台码
     */
    @Override
    public void forgetRelation(String userId, String platform) {
        if (userId == null || userId.isBlank() || platform == null || platform.isBlank()) {
            return;
        }
        redis.delete("union:relation:" + userId + ":" + platform);
    }

    /**
     * 先读缓存，未命中再查表。
     *
     * @param userId 当前用户
     * @param platform 平台码
     * @param read 按 key 读缓存，没有则空
     * @param write 把查到的身份写入缓存
     * @param load 缓存未命中时查表
     * @return 推广身份；没有则空
     */
    public static Optional<String> cached(
            String userId,
            String platform,
            Function<String, String> read,
            BiConsumer<String, String> write,
            BiFunction<String, String, Optional<String>> load) {
        if (userId == null || userId.isBlank() || platform == null || platform.isBlank()) {
            return Optional.empty();
        }
        String key = "union:relation:" + userId + ":" + platform;
        String hit = read.apply(key);
        if (hit != null && !hit.isBlank()) {
            return Optional.of(hit);
        }
        Optional<String> found = load.apply(userId, platform).filter(id -> !id.isBlank());
        found.ifPresent(id -> write.accept(key, id));
        return found;
    }

    /**
     * 从表里读推广身份。
     *
     * @param userId 当前用户
     * @param platform 平台码
     * @return 非空 relation_id；没有或平台码非法则空
     */
    private Optional<String> loadRelation(String userId, String platform) {
        Platform parsed = platformOf(platform);
        if (parsed == null || userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        PlatformAuthorization row = authorizations.selectOne(new LambdaQueryWrapper<PlatformAuthorization>()
                .eq(PlatformAuthorization::getUserId, userId)
                .eq(PlatformAuthorization::getPlatform, parsed));
        if (row == null || row.getRelationId() == null || row.getRelationId().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(row.getRelationId());
    }

    /**
     * 把请求里的平台码还原成枚举。认不出则空，调用方按未授权处理。
     *
     * @param platform 平台码
     * @return 枚举；空串或无法识别时为空
     */
    private static Platform platformOf(String platform) {
        try {
            return Platform.fromCode(platform);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
/**
 * 端上是否报过已安装对应 APP。
 */

    @Override
    public boolean installed(String userId, String platform) {
        Platform parsed = platformOf(platform);
        if (parsed == null) {
            return false;
        }
        AppUserInstalledApp row = installedApps.selectOne(new LambdaQueryWrapper<AppUserInstalledApp>()
                .eq(AppUserInstalledApp::getUserId, userId)
                .eq(AppUserInstalledApp::getPlatform, parsed));
        return row != null && Integer.valueOf(1).equals(row.getInstalled());
    }
/**
 * 记一次转链点击，供后续归因。
 */

    @Override
    public void click(String userId, String platform, String goodsId, String platformGoodsId) {
        ClickEvent event = new ClickEvent();
        event.setId(Ids.ulid());
        event.setUserId(userId);
        event.setPlatform(Platform.fromCode(platform));
        event.setGoodsId(goodsId);
        event.setPlatformGoodsId(platformGoodsId);
        event.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        clicks.insert(event);
    }
/**
 * 列出点击，供内部核对。
 */

    @Override
    public int clicks(String userId) {
        Long count = clicks.selectCount(new LambdaQueryWrapper<ClickEvent>().eq(ClickEvent::getUserId, userId));
        return count == null ? 0 : count.intValue();
    }
}
