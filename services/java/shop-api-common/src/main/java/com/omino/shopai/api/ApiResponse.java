package com.omino.shopai.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 统一信封。成功码是 0。
 *
 * @param code 业务码，0 为成功
 * @param message 给人看的说明
 * @param data 业务数据，失败时为空
 * @param traceId 本次响应的追踪号
 * @param ts 上海时区的时间
 * @param error 字段级错误，可空
 */
public record ApiResponse<T>(int code, String message, T data, String traceId, String ts, ApiError error) {

    /** 一次请求的追踪号放在这个请求属性上，过滤器和 Advice 共用。 */
    public static final String TRACE_ATTRIBUTE = "com.omino.shopai.traceId";

    /**
     * 没有字段级错误的信封。追踪号和时间先空着，写出前再填。
     *
     * @param code 业务码
     * @param message 给人看的说明
     * @param data 业务数据
     */
    public ApiResponse(int code, String message, T data) {
        this(code, message, data, null, null, null);
    }

    /**
     * 成功信封。追踪号和时间由写出前的 Advice 填入。
     *
     * @param data 业务数据
     * @param <T> 业务数据类型
     * @return code 为 0 的信封
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, null, null, null);
    }

    /**
     * 过滤器直接把 JSON 写进响应时用。这类响应不经过 Advice，所以这里就填上追踪号和时间。
     *
     * @param code 业务码
     * @param message 给人看的说明
     * @param traceId 请求上已有的追踪号；空则现生成
     * @return 失败信封
     */
    public static ApiResponse<Void> reject(int code, String message, Object traceId) {
        String trace = traceId instanceof String text && !text.isBlank() ? text : trace();
        return new ApiResponse<>(code, message, null, trace, now(), null);
    }

    /**
     * 生成一次请求的追踪号。方法不叫 traceId，因为记录组件已经占用了这个访问器名字。
     *
     * @return 去掉横线的随机号
     */
    public static String trace() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 当前上海时间。
     *
     * @return 带时区偏移的 ISO-8601 时间
     */
    public static String now() {
        return OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString();
    }
}
