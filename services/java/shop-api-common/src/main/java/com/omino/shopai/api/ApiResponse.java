package com.omino.shopai.api;

public record ApiResponse<T>(int code, String message, T data, String traceId, String ts) {

    public static <T> ApiResponse<T> ok(T data, String traceId, String ts) {
        return new ApiResponse<>(0, "ok", data, traceId, ts);
    }
}
