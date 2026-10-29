package com.omino.shopai.admin.service.wallet;

import com.omino.shopai.enums.WalletLedgerType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.WalletLedger;
import com.omino.shopai.entity.Withdrawal;
import com.omino.shopai.mapper.WalletLedgerMapper;
import com.omino.shopai.mapper.WalletMapper;
import com.omino.shopai.mapper.WithdrawalMapper;
import com.omino.shopai.support.Ids;
import com.omino.shopai.support.withdrawal.WithdrawalAccounts;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
/**
 * 持久化边界。事务和状态判断由调用方负责。
 */

@Repository
public class MybatisWithdrawalAccounts implements WithdrawalAccounts {

    private final WithdrawalMapper withdrawals;
    private final WalletMapper wallets;
    private final WalletLedgerMapper ledgers;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
/**
 * 提现审核需要的钱包锁和流水写入。
 */

    public MybatisWithdrawalAccounts(
            WithdrawalMapper withdrawals,
            WalletMapper wallets,
            WalletLedgerMapper ledgers,
            JdbcTemplate jdbc,
            PlatformTransactionManager manager) {
        this.withdrawals = withdrawals;
        this.wallets = wallets;
        this.ledgers = ledgers;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(manager);
    }

    @Override
    public Withdrawal find(String id) {
        return withdrawals.selectById(id);
    }

    @Override
    public Wallet wallet(String userId) {
        return wallets.selectById(userId);
    }
/**
 * 按用户锁钱包，打款和拒绝都在这把锁里改冻结额。
 */

    @Override
    public void lock(String userId, Runnable action) {
        tx.executeWithoutResult(status -> {
            jdbc.queryForList("SELECT user_id FROM wallet WHERE user_id = ? FOR UPDATE", String.class, userId);
            action.run();
        });
    }

    @Override
    public void save(Withdrawal withdrawal) {
        withdrawals.updateById(withdrawal);
    }

    @Override
    public void save(Wallet wallet) {
        wallets.updateById(wallet);
    }
/**
 * 打款成功后写提现流水。同一提现只记一次。
 */

    @Override
    public void addWithdrawLedger(Withdrawal withdrawal) {
        WalletLedger ledger = new WalletLedger();
        ledger.setId(Ids.ulid());
        ledger.setUserId(withdrawal.getUserId());
        ledger.setType(WalletLedgerType.WITHDRAW);
        ledger.setAmount(withdrawal.getAmount());
        ledger.setWithdrawalId(withdrawal.getId());
        ledger.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        ledgers.insert(ledger);
    }
/**
 * 数出该提现已有流水，用来挡住重复扣冻结。
 */

    @Override
    public int withdrawLedgerCount(String withdrawalId) {
        Long count = ledgers.selectCount(new LambdaQueryWrapper<WalletLedger>()
                .eq(WalletLedger::getType, WalletLedgerType.WITHDRAW)
                .eq(WalletLedger::getWithdrawalId, withdrawalId));
        return count == null ? 0 : count.intValue();
    }
}
