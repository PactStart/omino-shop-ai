package com.omino.shopai.support.withdrawal;

import java.math.BigDecimal;
/**
 * 打款端口。实现不得在失败时改冻结额。
 */

public interface IPayout {

    Result transfer(String withdrawalId, BigDecimal amount, String payeeAccount);
/**
 * 渠道返回的成败和转账单号。失败时单号为空。
 */

    record Result(boolean success, String transferId) {
        public static Result failed() {
            return new Result(false, null);
        }

        public static Result ok(String transferId) {
            return new Result(true, transferId);
        }
    }
}
