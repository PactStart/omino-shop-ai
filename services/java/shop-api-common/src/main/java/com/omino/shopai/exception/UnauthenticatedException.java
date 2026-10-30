package com.omino.shopai.exception;
/**
 * 未登录。HTTP 401，信封码 40100。
 */

public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException() {
        super("未登录或 access_token 失效");
    }
}
