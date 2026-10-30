package com.omino.shopai.app.service.wallet;
/**
 * 可用、冻结和累计。金额是字符串。
 */

public record WalletView(String availableAmount, String frozenAmount, String totalEarned) {
}
