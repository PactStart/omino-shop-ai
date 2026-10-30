package com.omino.shopai.support.withdrawal;

import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.Withdrawal;
/**
 * 提现用例需要的账户读写。
 */

public interface IWithdrawalAccount {

    Withdrawal find(String id);

    Wallet wallet(String userId);

    void lock(String userId, Runnable action);

    void save(Withdrawal withdrawal);

    void save(Wallet wallet);

    void addWithdrawLedger(Withdrawal withdrawal);

    int withdrawLedgerCount(String withdrawalId);
}
