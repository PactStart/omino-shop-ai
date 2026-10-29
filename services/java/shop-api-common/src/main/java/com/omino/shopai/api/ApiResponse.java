package com.omino.shopai.api;
/**
 * 统一信封。成功码是 0。
 */

public record ApiResponse<T>(int code, String message, T data, String traceId, String ts, ApiError error) {

    public ApiResponse(int code, String message, T data, String traceId, String ts) {
        this(code, message, data, traceId, ts, null);
    }

    public static <T> ApiResponse<T> ok(T data, String traceId, String ts) {
        return new ApiResponse<>(0, "ok", data, traceId, ts, null);
    }
}
