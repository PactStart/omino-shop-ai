package com.omino.shopai.admin.web.dto;

/**
 * 积分调账。
 *
 * @param userId 用户主键
 * @param amount 带符号的金额
 * @param memo 备注，必填
 */
public record CreditAdjustDto(String userId, String amount, String memo) {
}
