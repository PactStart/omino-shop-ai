package com.omino.shopai.app.dto;
/**
 * 可用、冻结和累计。金额是字符串。
 */

public record WalletViewDto(String availableAmount, String frozenAmount, String totalEarned) {
}
