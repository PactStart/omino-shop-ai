package com.omino.shopai.app.adapter.platform.limitrate;

import com.omino.shopai.app.config.UnionLimitProperties;
import com.omino.shopai.app.util.SdkLog;
import com.omino.shopai.app.util.UnionCallObserver;
import com.omino.shopai.exception.PlatformCallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 挂在 {@link SdkLog} 上的联盟出站计量。适配器不用各自计数。
 */
@Component
public class UnionCallMeter implements UnionCallObserver {

    private static final Logger log = LoggerFactory.getLogger(UnionCallMeter.class);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final long DAILY_TTL_SECONDS = 40L * 24 * 60 * 60;

    /**
     * 两个桶都够才扣。速率为 0 的那一档跳过，避免没配接口配额时误伤。
     * 用 Redis 的时钟，多台 APP 共用同一个账号配额。
     */
    private static final DefaultRedisScript<Long> TAKE = script("""
            local function now_ms()
              local t = redis.call('TIME')
              return tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            end
            local function refill(key, rate, now)
              local data = redis.call('HMGET', key, 'tokens', 'ts')
              local tokens = tonumber(data[1])
              local ts = tonumber(data[2])
              if tokens == nil then
                return rate
              end
              local elapsed = math.max(0, now - ts) / 1000.0
              if tokens + elapsed * rate > rate then
                return rate
              end
              return tokens + elapsed * rate
            end
            local now = now_ms()
            local rate1 = tonumber(ARGV[1])
            local rate2 = tonumber(ARGV[2])
            local tokens1 = 0
            local tokens2 = 0
            if rate1 > 0 then
              tokens1 = refill(KEYS[1], rate1, now)
              if tokens1 < 1 then
                return 0
              end
            end
            if rate2 > 0 then
              tokens2 = refill(KEYS[2], rate2, now)
              if tokens2 < 1 then
                return 0
              end
            end
            if rate1 > 0 then
              redis.call('HSET', KEYS[1], 'tokens', tokens1 - 1, 'ts', now)
              redis.call('PEXPIRE', KEYS[1], 60000)
            end
            if rate2 > 0 then
              redis.call('HSET', KEYS[2], 'tokens', tokens2 - 1, 'ts', now)
              redis.call('PEXPIRE', KEYS[2], 60000)
            end
            return 1
            """);

    private static final DefaultRedisScript<Long> COUNT = script("""
            local n = redis.call('HINCRBY', KEYS[1], ARGV[1], 1)
            local ttl = redis.call('TTL', KEYS[1])
            if ttl < 0 then
              redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
            end
            return n
            """);

    private final StringRedisTemplate redis;
    private final UnionLimitProperties limits;
    private final List<UnionLimitSignal> signals;
    private final List<UnionLimitAction> actions;
    private final Clock clock;

    /**
     * 绑到出站入口。之后每次官方调用都会经过这里。
     *
     * @param redis 配额和当日次数
     * @param limits 本地每秒配额，可热更新
     * @param signals 各平台怎么认出限流
     * @param actions 限流之后的动作
     * @param clock 用来算上海的自然日
     */
    public UnionCallMeter(
            StringRedisTemplate redis,
            UnionLimitProperties limits,
            List<UnionLimitSignal> signals,
            List<UnionLimitAction> actions,
            Clock clock) {
        this.redis = redis;
        this.limits = limits;
        this.signals = signals;
        this.actions = actions;
        this.clock = clock;
        SdkLog.bind(this);
    }

    /**
     * 平台合计和单个接口都要过。没配的那一档不扣。
     *
     * @param platform 平台码
     * @param api 本系统给这个接口起的名字
     */
    @Override
    public void before(String platform, String api) {
        int platformQps = qps(platform);
        int apiQps = qps(platform + ":" + api);
        // 两档都没配就直接出站。官方真限流仍由响应里的错误码识别。
        if (platformQps <= 0 && apiQps <= 0) {
            return;
        }
        if (!allowed(platform, api, platformQps, apiQps)) {
            log.info("sdk {} {} 本地配额已满", platform, api);
            throw new PlatformCallException("调用过于频繁");
        }
    }

    /**
     * 先记当日次数，再看是不是官方限流。本地拒绝的调用不会进到这里。
     *
     * @param platform 平台码
     * @param api 本系统给这个接口起的名字
     * @param response 官方响应；抛异常时为空
     * @param error 客户端异常；正常返回时为空
     */
    @Override
    public void after(String platform, String api, Object response, Exception error) {
        count(platform, api);
        UnionLimitHit hit = hit(platform, response, error);
        if (hit == null) {
            return;
        }
        log.warn("sdk {} {} 触发官方限流 {}", platform, api, hit.code());
        for (UnionLimitAction action : actions) {
            try {
                // 动作失败不能挡住已经拿到的官方响应，也不能改掉原来的异常。
                action.onLimited(platform, api, hit.code(), hit.message());
            } catch (RuntimeException ex) {
                log.warn("联盟限流动作失败 {} {}", platform, api);
            }
        }
    }

    /**
     * 读这一档每秒配额。没有或不是正数就当不限制。
     *
     * @param key 平台，或平台加接口
     * @return 每秒次数；不限制时为 0
     */
    private int qps(String key) {
        if (limits.getQps() == null) {
            return 0;
        }
        Integer rate = limits.getQps().get(key);
        if (rate == null || rate <= 0) {
            return 0;
        }
        return rate;
    }

    /**
     * 问 Redis 还能不能打。Redis 故障时放行，避免购物被缓存拖停。
     *
     * @param platform 平台码
     * @param api 接口名
     * @param platformQps 平台合计配额
     * @param apiQps 这个接口自己的配额
     * @return 可以出站时为真
     */
    private boolean allowed(String platform, String api, int platformQps, int apiQps) {
        try {
            Long ok = redis.execute(
                    TAKE,
                    List.of("union:qps:" + platform, "union:qps:" + platform + ":" + api),
                    Integer.toString(platformQps),
                    Integer.toString(apiQps));
            // 脚本没返回时无法判断，按放行处理，和 Redis 故障同一条路。
            return ok == null || ok == 1L;
        } catch (RuntimeException ex) {
            log.warn("联盟配额读取失败，本次放行 {} {}", platform, api);
            return true;
        }
    }

    /**
     * 上海自然日的调用次数加一。成功、业务失败和官方限流都算一次调用。
     *
     * @param platform 平台码
     * @param api 接口名
     */
    private void count(String platform, String api) {
        String day = LocalDate.now(clock.withZone(SHANGHAI)).format(DAY);
        try {
            redis.execute(
                    COUNT,
                    List.of("union:daily:" + day),
                    platform + ":" + api,
                    Long.toString(DAILY_TTL_SECONDS));
        } catch (RuntimeException ex) {
            log.warn("联盟调用计数失败 {} {}", platform, api);
        }
    }

    /**
     * 只问负责这个平台的判断。
     *
     * @param platform 平台码
     * @param response 官方响应，可空
     * @param error 客户端异常，可空
     * @return 限流；不是则空
     */
    private UnionLimitHit hit(String platform, Object response, Exception error) {
        for (UnionLimitSignal signal : signals) {
            if (platform.equals(signal.platform())) {
                UnionLimitHit hit = signal.hit(response, error);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /**
     * 把脚本文本收成 Redis 脚本。
     *
     * @param text Lua
     * @return 返回整数的脚本
     */
    private static DefaultRedisScript<Long> script(String text) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(text);
        script.setResultType(Long.class);
        return script;
    }
}
