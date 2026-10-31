package com.omino.shopai.app.web.app.dto;

/**
 * 提现申请。渠道在服务里收成枚举。
 */
public record ApplyRequestDto(String amount, String payeeAccount, String channel) {
}
