package com.omino.shopai.api;
/**
 * 信封里的字段错误。没有时为空。
 */

public record ApiError(String field, String reason) {
}
