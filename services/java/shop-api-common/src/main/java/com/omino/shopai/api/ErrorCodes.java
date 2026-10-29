package com.omino.shopai.api;
/**
 * 业务错误码。传输层状态和这里的码分开。
 */

public final class ErrorCodes {

    public static final int OK = 0;
    public static final int VALIDATION = 40001;
    public static final int UNAUTHENTICATED = 40100;
    public static final int FORBIDDEN = 40300;
    public static final int NEED_AUTH = 40301;
    public static final int GOD_VIEW_DISABLED = 40303;
    public static final int NOT_FOUND = 40400;
    public static final int CONFLICT = 40900;
    public static final int INTERNAL = 50000;
    public static final int UPSTREAM_PLATFORM = 50010;
    public static final int AGENT = 50020;

    private ErrorCodes() {
    }
}
