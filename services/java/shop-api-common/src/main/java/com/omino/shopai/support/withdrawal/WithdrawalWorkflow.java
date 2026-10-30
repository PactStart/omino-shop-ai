package com.omino.shopai.support.withdrawal;

import com.omino.shopai.enums.WithdrawalStatus;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.Withdrawal;

import java.time.Clock;
import java.time.OffsetDateTime;
/**
 * 在用户锁里执行打款或拒绝。
 */

public final class WithdrawalWorkflow {
/**
 * 给定当前状态和渠道结果，算出下一步。
 */

    @FunctionalInterface
    public interface PayoutDecision {
        WithdrawalTransition.Decision apply(WithdrawalStatus status, boolean success, String transferId);
    }
/**
 * 给定当前状态，算出拒绝时要不要解冻。
 */

    @FunctionalInterface
    public interface RejectDecision {
        WithdrawalTransition.Decision apply(WithdrawalStatus status);
    }

    private final IWithdrawalAccount accounts;
    private final IPayout port;
    private final Clock clock;
/**
 * 打款和拒绝都在用户锁里改冻结额。
 */

    public WithdrawalWorkflow(IWithdrawalAccount accounts, IPayout port, Clock clock) {
        this.accounts = accounts;
        this.port = port;
        this.clock = clock;
    }

    public PayoutViewDto payout(String id) {
        return payout(id, port, WithdrawalTransition::afterPayout);
    }
/**
 * 按打款结果推进提现状态。
 */

    public PayoutViewDto payout(String id, IPayout payouts, PayoutDecision decide) {
        Withdrawal loaded = accounts.find(id);
        if (loaded == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "提现不存在");
        }
        PayoutViewDto[] box = new PayoutViewDto[1];
        accounts.lock(loaded.getUserId(), () -> box[0] = payoutLocked(id, payouts, decide));
        return box[0];
    }

    public void reject(String id, String reason) {
        reject(id, reason, WithdrawalTransition::reject);
    }
/**
 * 拒绝并解开冻结。
 */

    public void reject(String id, String reason, RejectDecision decide) {
        Withdrawal loaded = accounts.find(id);
        if (loaded == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "提现不存在");
        }
        accounts.lock(loaded.getUserId(), () -> rejectLocked(id, reason, decide));
    }
/**
 * 按打款结果推进提现状态。
 */

    private PayoutViewDto payoutLocked(String id, IPayout payouts, PayoutDecision decide) {
        // 锁内重读。已经不是待打款就不再调渠道，避免冻结被扣两次。
        Withdrawal current = accounts.find(id);
        if (current == null || !WithdrawalTransition.shouldCallPort(current.getStatus())) {
            String status = current == null ? WithdrawalStatus.PENDING.code() : current.getStatus().code();
            return new PayoutViewDto("ok", status, status);
        }
        IPayout.Result result = payouts.transfer(current.getId(), current.getAmount(), current.getPayeeAccount());
        WithdrawalTransition.Decision decision = decide.apply(current.getStatus(), result.success(), result.transferId());
        // 渠道失败时不扣冻结，冻结额保持不动。
        if (!decision.deductFrozen()) {
            return new PayoutViewDto(decision.message(), decision.payoutResult(), decision.status().code());
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        Wallet wallet = accounts.wallet(current.getUserId());
        WalletMath.deductFrozen(wallet, current.getAmount(), now);
        current.setStatus(decision.status());
        current.setProviderTransferId(decision.providerTransferId());
        current.setPaidAt(now);
        current.setUpdatedAt(now);
        accounts.save(current);
        accounts.save(wallet);
        if (decision.writeLedger()) {
            accounts.addWithdrawLedger(current);
        }
        return new PayoutViewDto(decision.message(), decision.payoutResult(), decision.status().code());
    }
/**
 * 拒绝并解开冻结。
 */

    private void rejectLocked(String id, String reason, RejectDecision decide) {
        Withdrawal current = accounts.find(id);
        if (current == null) {
            return;
        }
        WithdrawalTransition.Decision decision = decide.apply(current.getStatus());
        if (!decision.unfreeze()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        Wallet wallet = accounts.wallet(current.getUserId());
        WalletMath.unfreeze(wallet, current.getAmount(), now);
        current.setStatus(WithdrawalStatus.REJECTED);
        current.setRejectReason(reason);
        current.setUpdatedAt(now);
        accounts.save(current);
        accounts.save(wallet);
    }
}
