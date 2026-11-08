package com.omino.shopai.app.web.app.dto;

/**
 * 注销确认。必须原样带上 DELETE。
 *
 * @param confirm 确认词
 */
public record DeleteAccountDto(String confirm) {
}
