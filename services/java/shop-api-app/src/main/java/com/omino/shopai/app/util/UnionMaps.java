package com.omino.shopai.app.util;

import com.omino.shopai.exception.PlatformCallException;

import com.omino.shopai.enums.OrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
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
    /** 一段里最多翻这么多页。页还满则本段失败，避免水位跨过没拉到的订单。 */
    public static final int MAX_ORDER_PAGES = 40;
    public static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter OFFICIAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
     * 这个平台一段查询允许的最长时间。淘宝日常 3 小时，其余平台 1 小时。
     *
     * @param platform 平台码
     * @return 一段的最大跨度
     */
    public static Duration maxSpan(String platform) {
        if ("taobao".equals(platform)) {
            return Duration.ofHours(3);
        }
        return Duration.ofHours(1);
    }

    /**
     * 从水位到本次请求时间之间，切出下一段。没有水位时只补最近一小时。已经追上则没有下一段。
     *
     * @param cursor 这一列的水位；空表示还没拉过
     * @param until 本次请求时间，循环期间不再往后挪
     * @param max 这一段允许的最大跨度
     * @return 下一段；不需要再拉时为空
     */
    public static Window slice(OffsetDateTime cursor, OffsetDateTime until, Duration max) {
        if (until == null || max == null || max.isZero() || max.isNegative()) {
            return null;
        }
        OffsetDateTime start = cursor == null ? until.minusHours(1) : cursor;
        if (!start.isBefore(until)) {
            return null;
        }
        OffsetDateTime end = start.plus(max);
        if (end.isAfter(until)) {
            end = until;
        }
        if (!start.isBefore(end)) {
            return null;
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
     * 淘宝订单状态。12 已付款，3 与 14 已完成，13 已取消，其余（含空）待付款。
     *
     * @param tkStatus 淘宝订单状态码
     * @return 本系统订单状态
     */
    public static OrderStatus taobaoStatus(Long tkStatus) {
        if (tkStatus == null) {
            return OrderStatus.PENDING_PAY;
        }
        return switch (tkStatus.intValue()) {
            case 12 -> OrderStatus.PENDING_SHIP;
            case 3, 14 -> OrderStatus.COMPLETED;
            case 13 -> OrderStatus.CANCELLED;
            default -> OrderStatus.PENDING_PAY;
        };
    }

    /**
     * 京东有效码。15 待付款，16 已付款，17 已完成，24 已付定金，其余（含空）待付款。
     *
     * @param validCode 京东有效码
     * @return 本系统订单状态
     */
    public static OrderStatus jdStatus(Integer validCode) {
        if (validCode == null) {
            return OrderStatus.PENDING_PAY;
        }
        return switch (validCode) {
            case 15 -> OrderStatus.PENDING_PAY;
            case 16, 24 -> OrderStatus.PENDING_SHIP;
            case 17 -> OrderStatus.COMPLETED;
            default -> OrderStatus.PENDING_PAY;
        };
    }

    /**
     * 拼多多订单状态。0 已付款，1 已发货，2 已收货，3 与 5 已完成，4 与 10 已取消，其余（含空）待付款。
     * 已收货写成已发货，避免当成已完成提前入账。
     *
     * @param status 拼多多订单状态
     * @return 本系统订单状态
     */
    public static OrderStatus pddStatus(Integer status) {
        if (status == null) {
            return OrderStatus.PENDING_PAY;
        }
        return switch (status) {
            case 0 -> OrderStatus.PENDING_SHIP;
            case 1, 2 -> OrderStatus.SHIPPED;
            case 3, 5 -> OrderStatus.COMPLETED;
            case 4, 10 -> OrderStatus.CANCELLED;
            default -> OrderStatus.PENDING_PAY;
        };
    }

    /**
     * 唯品会订单状态。0 已取消，1 待付款，2 已完成，其余（含空）待付款。不看结算标记。
     *
     * @param status 唯品会订单状态
     * @return 本系统订单状态
     */
    public static OrderStatus vipStatus(Short status) {
        if (status == null) {
            return OrderStatus.PENDING_PAY;
        }
        return switch (status.intValue()) {
            case 0 -> OrderStatus.CANCELLED;
            case 1 -> OrderStatus.PENDING_PAY;
            case 2 -> OrderStatus.COMPLETED;
            default -> OrderStatus.PENDING_PAY;
        };
    }

    /** 一次增量的起止时间。 */
    public record Window(OffsetDateTime start, OffsetDateTime end) {
    }
}
