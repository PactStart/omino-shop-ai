package com.omino.shopai.admin.service.wallet;

import com.omino.shopai.support.withdrawal.PayoutPort;
import com.omino.shopai.support.withdrawal.PayoutView;
import com.omino.shopai.support.withdrawal.WithdrawalAccounts;
import com.omino.shopai.support.withdrawal.WithdrawalTransition;
import com.omino.shopai.support.withdrawal.WithdrawalWorkflow;
import org.springframework.stereotype.Service;

import java.time.Clock;
/**
 * AdminWithdrawalService 编排用例，事务和校验放在这里。
 */

@Service
public class AdminWithdrawalService {

    private final WithdrawalWorkflow workflow;
    private final PayoutPort payouts;
/**
 * 注入打款流程。审核通过才调渠道。
 */

    public AdminWithdrawalService(WithdrawalAccounts accounts, PayoutPort payouts) {
        this.payouts = payouts;
        this.workflow = new WithdrawalWorkflow(accounts, payouts, Clock.systemUTC());
    }

    public PayoutView payout(String id) {
        return workflow.payout(id, payouts, WithdrawalTransition::afterPayout);
    }

    public void reject(String id, String reason) {
        workflow.reject(id, reason, WithdrawalTransition::reject);
    }
}
