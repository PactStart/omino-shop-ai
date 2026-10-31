package com.omino.shopai.app.dto;
/**
 * 用户看到的提现单。账号已掩码。
 */

public record WithdrawalViewDto(
        String id,
        String amount,
        String status,
        String channel,
        String accountMask,
        String createdAt) {
}
