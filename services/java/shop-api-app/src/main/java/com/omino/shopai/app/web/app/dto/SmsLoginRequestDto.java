package com.omino.shopai.app.web.app.dto;

/**
 * 手机号和验证码。
 */
public record SmsLoginRequestDto(String phone, String code, String inviteCode) {
}
