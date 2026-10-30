package com.omino.shopai.app.adapter.platform;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * 官方字段到本系统字段的转换。失败文案不从这里产出。
 */
public final class UnionMaps {

    public static final int TIMEOUT_MS = 10_000;
    public static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter OFFICIAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern USER_ID = Pattern.compile("[0-9A-HJKMNP-TV-Za-hjkmnp-tv-z]{26}");
    private static final Pattern USER_JSON = Pattern.compile(
            "\\{\"uid\":\"([0-9A-HJKMNP-TV-Za-hjkmnp-tv-z]{26})\"\\}");

    private UnionMaps() {
    }

    /** 金额，单位元。解析不了则空。 */
    public static BigDecimal money(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 金额，单位元。 */
    public static BigDecimal money(Double raw) {
        if (raw == null || raw.isNaN() || raw.isInfinite()) {
            return null;
        }
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
    }

    /** 分转元。 */
    public static BigDecimal fen(Long fen) {
        if (fen == null) {
            return null;
        }
        return BigDecimal.valueOf(fen).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }

    /** 补全协议相对地址。 */
    public static String http(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.startsWith("//")) {
            return "https:" + raw;
        }
        return raw;
    }

    /** 取第一个非空链接。 */
    public static String first(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String url = http(value);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    /** 推广身份必须非空白，否则不出站。 */
    public static String promotion(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new PlatformCallException();
        }
        return raw;
    }

    /**
     * 只接受本系统用户号，或拼多多那种只含用户号的 JSON。其它字符串不写入订单。
     */
    public static String userId(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (USER_ID.matcher(trimmed).matches()) {
            return trimmed;
        }
        var json = USER_JSON.matcher(trimmed);
        if (json.matches()) {
            return json.group(1);
        }
        return null;
    }

    /** 查询窗口。没有水位时只看最近一小时，避免第一次就扫全历史。 */
    public static Window window(OffsetDateTime cursor) {
        OffsetDateTime end = OffsetDateTime.now(SHANGHAI);
        OffsetDateTime start = cursor == null ? end.minusHours(1) : cursor;
        if (!start.isBefore(end)) {
            start = end.minusMinutes(1);
        }
        return new Window(start, end);
    }

    /** 官方接口要的本地时间字符串。 */
    public static String format(OffsetDateTime time) {
        return OFFICIAL_TIME.format(time.atZoneSameInstant(SHANGHAI));
    }

    /** 解析官方时间。解析不了则空。 */
    public static OffsetDateTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim(), OFFICIAL_TIME).atZone(SHANGHAI).toOffsetDateTime();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /** 秒或毫秒时间戳。 */
    public static OffsetDateTime fromEpoch(Long raw) {
        if (raw == null || raw <= 0) {
            return null;
        }
        Instant instant = raw > 1_000_000_000_000L ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw);
        return OffsetDateTime.ofInstant(instant, SHANGHAI);
    }

    /**
     * 淘宝订单状态。对照参考实现：12 已付款，3 与 14 已完成，13 已取消，其余（含空）待付款。
     */
    public static String taobaoStatus(Long tkStatus) {
        if (tkStatus == null) {
            return "pending_pay";
        }
        return switch (tkStatus.intValue()) {
            case 12 -> "pending_ship";
            case 3, 14 -> "completed";
            case 13 -> "cancelled";
            default -> "pending_pay";
        };
    }

    /**
     * 京东有效码。对照参考实现：15 待付款，16 已付款，17 已完成，24 已付定金，其余（含空）待付款。
     */
    public static String jdStatus(Integer validCode) {
        if (validCode == null) {
            return "pending_pay";
        }
        return switch (validCode) {
            case 15 -> "pending_pay";
            case 16, 24 -> "pending_ship";
            case 17 -> "completed";
            default -> "pending_pay";
        };
    }

    /**
     * 拼多多订单状态。对照参考实现：0 已付款，1 已发货，2 已收货，3 与 5 已完成，4 与 10 已取消，其余（含空）待付款。
     * 已收货写成已发货，避免当成已完成提前入账。
     */
    public static String pddStatus(Integer status) {
        if (status == null) {
            return "pending_pay";
        }
        return switch (status) {
            case 0 -> "pending_ship";
            case 1, 2 -> "shipped";
            case 3, 5 -> "completed";
            case 4, 10 -> "cancelled";
            default -> "pending_pay";
        };
    }

    /**
     * 唯品会订单状态。对照参考实现：0 已取消，1 待付款，2 已完成，其余（含空）待付款。不看结算标记。
     */
    public static String vipStatus(Short status) {
        if (status == null) {
            return "pending_pay";
        }
        return switch (status.intValue()) {
            case 0 -> "cancelled";
            case 1 -> "pending_pay";
            case 2 -> "completed";
            default -> "pending_pay";
        };
    }

    /** 一次增量的起止时间。 */
    public record Window(OffsetDateTime start, OffsetDateTime end) {
    }
}
