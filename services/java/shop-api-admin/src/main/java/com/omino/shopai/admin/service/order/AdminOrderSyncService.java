package com.omino.shopai.admin.service.order;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.AdminAuditLog;
import com.omino.shopai.entity.OrderSyncCursor;
import com.omino.shopai.enums.Platform;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.AdminAuditLogMapper;
import com.omino.shopai.mapper.OrderSyncCursorMapper;
import com.omino.shopai.support.Ids;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 四个平台的同步游标，以及先写审计再触发一次既有增量。
 */
@Service
public class AdminOrderSyncService {

    private final OrderSyncCursorMapper cursors;
    private final Consumer<AdminAuditLog> audits;
    private final Consumer<String> trigger;

    /**
     * 用审计表和 APP 内部地址组装重试。令牌只从环境读取，不写进配置文件。
     *
     * @param cursors 游标表
     * @param audits 审计表
     * @param appBaseUrl APP 进程根，如 http://127.0.0.1:8181
     * @param token 内部令牌
     */
    @Autowired
    public AdminOrderSyncService(
            OrderSyncCursorMapper cursors,
            AdminAuditLogMapper audits,
            @Value("${shop.app-base-url:http://127.0.0.1:8181}") String appBaseUrl,
            @Value("${AGENT_INTERNAL_TOKEN:}") String token) {
        this(cursors, audits::insertJson, platform -> post(appBaseUrl, token, platform));
    }

    /**
     * 测试用。先记审计，再调用传入的触发器。
     *
     * @param cursors 游标表，查询时可空
     * @param audits 审计写入
     * @param trigger 真正去拉增量的动作
     */
    public AdminOrderSyncService(OrderSyncCursorMapper cursors, Consumer<AdminAuditLog> audits, Consumer<String> trigger) {
        this.cursors = cursors;
        this.audits = audits;
        this.trigger = trigger;
    }

    /**
     * 固定返回四个平台。库里没有的行，水位和错误都是空。
     *
     * @return 四行游标
     */
    public List<Map<String, Object>> list() {
        Map<Platform, OrderSyncCursor> found = new LinkedHashMap<>();
        if (cursors != null) {
            for (OrderSyncCursor cursor : cursors.selectList(null)) {
                if (cursor.getPlatform() != null) {
                    found.put(cursor.getPlatform(), cursor);
                }
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Platform platform : Platform.values()) {
            OrderSyncCursor cursor = found.get(platform);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("platform", platform.code());
            row.put("lastSyncCreateTime", cursor == null ? null : cursor.getLastSyncCreateTime());
            row.put("lastSyncUpdateTime", cursor == null ? null : cursor.getLastSyncUpdateTime());
            row.put("lastError", cursor == null ? null : cursor.getLastError());
            row.put("updatedAt", cursor == null ? null : cursor.getUpdatedAt());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 平台不是四个之一则拒绝，并且不触发 APP。合法平台先写审计，明细只有平台码。
     *
     * @param platform 平台码
     * @param ssoSub 操作人
     */
    public void retry(String platform, String ssoSub) {
        String code = requirePlatform(platform);
        AdminAuditLog log = new AdminAuditLog();
        log.setId(Ids.ulid());
        log.setSsoSub(ssoSub == null ? "admin" : ssoSub);
        log.setAction("order.sync.retry");
        log.setResource(code);
        // detail 列是 jsonb。只放平台码这一个 JSON 字符串，不放密钥。
        log.setDetail("\"" + code + "\"");
        log.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        // 审计先落库。后面的出站失败也不能把这次重试从审计里抹掉。
        audits.accept(log);
        trigger.accept(code);
    }

    /**
     * 改一个平台的两条水位。没有这一行时插入。不改最近错误。写入成功后记审计。
     *
     * @param platform 平台码
     * @param lastSyncCreateTime 下单水位，空或空串表示清掉
     * @param lastSyncUpdateTime 更新水位，空或空串表示清掉
     * @param ssoSub 操作人
     */
    public void saveCursor(String platform, String lastSyncCreateTime, String lastSyncUpdateTime, String ssoSub) {
        String code = requirePlatform(platform);
        OffsetDateTime create = time(lastSyncCreateTime);
        OffsetDateTime update = time(lastSyncUpdateTime);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Platform value = Platform.fromCode(code);
        // set 会把空水位写成 NULL。updateById 会跳过空字段，清不掉已有水位。
        int changed = cursors.update(null, new UpdateWrapper<OrderSyncCursor>()
                .eq("platform", code)
                .set("last_sync_create_time", create)
                .set("last_sync_update_time", update)
                .set("updated_at", now));
        if (changed == 0) {
            OrderSyncCursor row = new OrderSyncCursor();
            row.setPlatform(value);
            row.setLastSyncCreateTime(create);
            row.setLastSyncUpdateTime(update);
            row.setUpdatedAt(now);
            cursors.insert(row);
        }
        AdminAuditLog log = new AdminAuditLog();
        log.setId(Ids.ulid());
        log.setSsoSub(ssoSub == null ? "admin" : ssoSub);
        log.setAction("order.sync.cursor");
        log.setResource(code);
        log.setDetail(cursorDetail(create, update));
        log.setCreatedAt(now);
        audits.accept(log);
    }

    /**
     * 只接受四个平台码。
     *
     * @param platform 调用方传入的平台
     * @return 规范的平台码
     */
    public static String requirePlatform(String platform) {
        try {
            Platform value = Platform.fromCode(platform);
            if (value == null) {
                throw new ApiBusinessException(ErrorCodes.VALIDATION, "平台不正确");
            }
            return value.code();
        } catch (IllegalArgumentException ex) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "平台不正确");
        }
    }

    /**
     * 把请求里的时间还原成带时区的时刻。空串视为清掉。
     *
     * @param raw 请求里的时间
     * @return 水位；空请求返回 null
     */
    private static OffsetDateTime time(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw.trim());
        } catch (DateTimeException ex) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "水位时间不正确");
        }
    }

    /**
     * 审计明细只放两条新水位，方便事后核对改到了哪里。
     *
     * @param create 下单水位
     * @param update 更新水位
     * @return JSON 对象文本
     */
    private static String cursorDetail(OffsetDateTime create, OffsetDateTime update) {
        return "{\"lastSyncCreateTime\":" + jsonTime(create) + ",\"lastSyncUpdateTime\":" + jsonTime(update) + "}";
    }

    /**
     * 把一个时刻写成 JSON 字符串。空水位写成 null。
     *
     * @param time 水位
     * @return JSON 值
     */
    private static String jsonTime(OffsetDateTime time) {
        return time == null ? "null" : "\"" + time + "\"";
    }

    private static void post(String appBaseUrl, String token, String platform) {
        String root = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
        RestClient.create().post()
                .uri(root + "/api/app/internal/v1/order-sync/{platform}", platform)
                .header("X-Internal-Token", token)
                .header("X-User-Id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .toBodilessEntity();
    }
}
