package com.omino.shopai.app.service.wallet;

import java.util.List;
/**
 * 一页提现。
 */

public record WithdrawalPage(List<WithdrawalView> list, int page, int size, long total) {
}
