package com.omino.shopai.support.withdrawal;

import com.omino.shopai.enums.WithdrawalStatus;
import com.omino.shopai.enums.PayoutChannel;
import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
/**
 * 提现状态机。失败停在待打款，冻结额不动。
 */

public final class WithdrawalTransition {

    public static final String FAIL_MESSAGE = "打款未成功，冻结额未动";

    private WithdrawalTransition() {
    }
/**
 * 打款或拒绝之后要不要动冻结、写流水，以及对外文案。
 */

    public record Decision(
            WithdrawalStatus status,
            boolean deductFrozen,
            boolean unfreeze,
            boolean writeLedger,
            String providerTransferId,
            String message,
            String payoutResult) {
    }

    public static boolean shouldCallPort(WithdrawalStatus status) {
        return status == WithdrawalStatus.PENDING;
    }
/**
 * 按渠道结果决定是否扣冻结。失败停在待打款。
 */

    public static Decision afterPayout(WithdrawalStatus status, boolean success, String transferId) {
        // 已经离开待打款，就不再调渠道，避免同一笔冻结被扣两次。
        if (!shouldCallPort(status)) {
            return keep(status);
        }
        // 渠道没成功时停在待打款，冻结额保持不动。
        if (!success) {
            return new Decision(WithdrawalStatus.PENDING, false, false, false, null, FAIL_MESSAGE, "failed");
        }
        // 单号超长说明渠道回包异常，不能当成已打款落库。
        if (transferId != null && transferId.length() > 64) {
            throw new IllegalArgumentException("providerTransferId");
        }
        return new Decision(WithdrawalStatus.PAID, true, false, true, transferId, "ok", "paid");
    }
/**
 * 拒绝并解开冻结。
 */

    public static Decision reject(WithdrawalStatus status) {
        if (!shouldCallPort(status)) {
            return keep(status);
        }
        return new Decision(WithdrawalStatus.REJECTED, false, true, false, null, "ok", "rejected");
    }

    /** 请求里的渠道字符串只接受支付宝。实体落库时写成 {@link PayoutChannel#ALIPAY}。 */
    public static void requireAlipay(String channel) {
        if (channel != null && !channel.isBlank() && !PayoutChannel.ALIPAY.code().equals(channel)) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "渠道不正确");
        }
    }

    private static Decision keep(WithdrawalStatus status) {
        return new Decision(status, false, false, false, null, "ok", status.code());
    }
}
