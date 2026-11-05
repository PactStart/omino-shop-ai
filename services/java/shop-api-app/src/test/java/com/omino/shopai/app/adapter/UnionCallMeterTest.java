package com.omino.shopai.app.adapter;

import com.omino.shopai.app.adapter.platform.limitrate.NoopUnionLimitAction;
import com.omino.shopai.app.adapter.platform.limitrate.TaobaoLimitSignal;
import com.omino.shopai.app.adapter.platform.limitrate.UnionCallMeter;
import com.omino.shopai.app.adapter.platform.limitrate.UnionLimitAction;
import com.omino.shopai.app.config.UnionLimitProperties;
import com.omino.shopai.app.util.SdkLog;
import com.omino.shopai.exception.PlatformCallException;
import com.taobao.api.response.TbkOrderDetailsGetResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnionCallMeterTest {

    private final List<String> seen = new ArrayList<>();

    @AfterEach
    void unbind() {
        SdkLog.bind(null);
    }

    @Test
    void taobaoLimitInTheBodyIsCountedAndHandedToTheAction() throws Exception {
        Scripts redis = new Scripts();
        meter(redis, Map.of());
        TbkOrderDetailsGetResponse response = new TbkOrderDetailsGetResponse();
        response.setSubCode("9100");
        response.setSubMsg("当前触发限流");
        Object returned = SdkLog.exchange("taobao", "order.details", Map.of(), () -> response);
        assertEquals(response, returned);
        assertEquals(List.of("union:daily:20261105 taobao:order.details"), redis.counts);
        assertEquals(List.of("taobao order.details 9100"), seen);
    }

    @Test
    void otherTaobaoFailureIsOnlyCounted() throws Exception {
        Scripts redis = new Scripts();
        meter(redis, Map.of());
        TbkOrderDetailsGetResponse response = new TbkOrderDetailsGetResponse();
        response.setSubCode("15");
        SdkLog.exchange("taobao", "order.details", Map.of(), () -> response);
        assertEquals(1, redis.counts.size());
        assertTrue(seen.isEmpty());
    }

    @Test
    void localQuotaStopsTheCallBeforeTheSdk() {
        Scripts redis = new Scripts();
        redis.permit = 0;
        meter(redis, Map.of("taobao", 30, "taobao:order.details", 5));
        AtomicInteger calls = new AtomicInteger();
        assertThrows(PlatformCallException.class, () -> SdkLog.exchange("taobao", "order.details", Map.of(), () -> {
            calls.incrementAndGet();
            return "ok";
        }));
        assertEquals(0, calls.get());
        assertTrue(redis.counts.isEmpty());
        assertTrue(seen.isEmpty());
    }

    @Test
    void redisOutageStillLetsTheCallThrough() throws Exception {
        Scripts redis = new Scripts();
        redis.down = true;
        meter(redis, Map.of("taobao", 30));
        assertEquals("ok", SdkLog.exchange("taobao", "item.info", Map.of(), () -> "ok"));
    }

    @Test
    void actionFailureDoesNotHideTheResponse() throws Exception {
        Scripts redis = new Scripts();
        UnionLimitProperties limits = new UnionLimitProperties();
        UnionLimitAction broken = (platform, api, code, message) -> {
            throw new IllegalStateException("动作坏了");
        };
        new UnionCallMeter(redis, limits, List.of(new TaobaoLimitSignal()), List.of(broken), clock());
        TbkOrderDetailsGetResponse response = new TbkOrderDetailsGetResponse();
        response.setSubCode("9100");
        assertEquals(response, SdkLog.exchange("taobao", "order.details", Map.of(), () -> response));
    }

    @Test
    void emptyActionAcceptsTheHit() {
        new NoopUnionLimitAction().onLimited("taobao", "order.details", "9100", "当前触发限流");
    }

    private void meter(Scripts redis, Map<String, Integer> qps) {
        UnionLimitProperties limits = new UnionLimitProperties();
        limits.setQps(qps);
        UnionLimitAction action = (platform, api, code, message) -> seen.add(platform + " " + api + " " + code);
        new UnionCallMeter(redis, limits, List.of(new TaobaoLimitSignal()), List.of(action), clock());
    }

    private static Clock clock() {
        return Clock.fixed(Instant.parse("2026-11-04T16:30:00Z"), ZoneOffset.UTC);
    }

    /**
     * 只回答脚本结果，不连真的 Redis。
     */
    private static final class Scripts extends StringRedisTemplate {

        private int permit = 1;
        private boolean down;
        private final List<String> counts = new ArrayList<>();

        /**
         * 计数脚本记下日期和字段。配额脚本按预设的许可返回。
         *
         * @param script 配额或计数脚本
         * @param keys Redis 键
         * @param args 脚本参数
         * @param <T> 脚本返回类型
         * @return 脚本结果
         */
        @Override
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            if (down) {
                throw new RedisConnectionFailureException("down");
            }
            if (script.getScriptAsString().contains("HINCRBY")) {
                counts.add(keys.get(0) + " " + args[0]);
                return cast(1L);
            }
            return cast((long) permit);
        }

        /**
         * 把整数收成脚本声明的返回类型。
         *
         * @param value 整数结果
         * @param <T> 脚本返回类型
         * @return 同一整数
         */
        @SuppressWarnings("unchecked")
        private static <T> T cast(Long value) {
            return (T) value;
        }
    }
}
