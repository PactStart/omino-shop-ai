package com.omino.shopai.app.adapter.platform.limitrate;

/**
 * 一次被认出的官方限流。
 *
 * @param code 平台给出的限流码
 * @param message 平台给出的说明，可空
 */
public record UnionLimitHit(String code, String message) {
}
