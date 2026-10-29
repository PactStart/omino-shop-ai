package com.omino.shopai.app.service.credit;

import com.omino.shopai.enums.ActiveStatus;
import com.omino.shopai.enums.CreditLedgerType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.entity.CreditAccount;
import com.omino.shopai.entity.CreditLedger;
import com.omino.shopai.entity.CreditOrder;
import com.omino.shopai.entity.CreditPackage;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.WalletLedger;
import com.omino.shopai.mapper.CreditAccountMapper;
import com.omino.shopai.mapper.CreditLedgerMapper;
import com.omino.shopai.mapper.CreditOrderMapper;
import com.omino.shopai.mapper.CreditPackageMapper;
import com.omino.shopai.mapper.WalletLedgerMapper;
import com.omino.shopai.mapper.WalletMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;
/**
 * 持久化边界。事务和状态判断由调用方负责。
 */

@Repository
public class MybatisCreditBooks implements CreditBooks {

    private final CreditAccountMapper accounts;
    private final CreditLedgerMapper ledgers;
    private final CreditOrderMapper orders;
    private final CreditPackageMapper packages;
    private final WalletMapper wallets;
    private final WalletLedgerMapper walletLedgers;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
/**
 * 把积分与钱包的读写交给 Mapper。事务由调用方包住。
 */

    public MybatisCreditBooks(
            CreditAccountMapper accounts,
            CreditLedgerMapper ledgers,
            CreditOrderMapper orders,
            CreditPackageMapper packages,
            WalletMapper wallets,
            WalletLedgerMapper walletLedgers,
            JdbcTemplate jdbc,
            TransactionTemplate transactions) {
        this.accounts = accounts;
        this.ledgers = ledgers;
        this.orders = orders;
        this.packages = packages;
        this.wallets = wallets;
        this.walletLedgers = walletLedgers;
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Override
    public <T> T exclusively(String userId, Supplier<T> action) {
        return transactions.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now();
            jdbc.update("""
                    INSERT INTO wallet (user_id, available_amount, frozen_amount, total_earned, updated_at)
                    VALUES (?, 0, 0, 0, ?)
                    ON CONFLICT (user_id) DO NOTHING
                    """, userId, now);
            jdbc.queryForList("SELECT user_id FROM wallet WHERE user_id = ? FOR UPDATE", String.class, userId);
            return action.get();
        });
    }

    @Override
    public Wallet wallet(String userId) {
        return wallets.selectById(userId);
    }
/**
 * 保存修改。
 */

    @Override
    public void saveWallet(Wallet wallet) {
        if (wallets.selectById(wallet.getUserId()) == null) {
            wallets.insert(wallet);
        } else {
            wallets.updateById(wallet);
        }
    }

    @Override
    public void addWalletLedger(WalletLedger ledger) {
        walletLedgers.insert(ledger);
    }

    @Override
    public CreditAccount creditAccount(String userId) {
        return accounts.selectById(userId);
    }
/**
 * 保存修改。
 */

    @Override
    public void saveCreditAccount(CreditAccount account) {
        if (accounts.selectById(account.getUserId()) == null) {
            accounts.insert(account);
        } else {
            accounts.updateById(account);
        }
    }

    @Override
    public void addCreditLedger(CreditLedger ledger) {
        ledgers.insert(ledger);
    }
/**
 * 数出购买类积分流水，用来确认回调没有重复入账。
 */

    @Override
    public int purchaseCount(String creditOrderId) {
        Long count = ledgers.selectCount(new LambdaQueryWrapper<CreditLedger>()
                .eq(CreditLedger::getType, CreditLedgerType.PURCHASE)
                .eq(CreditLedger::getCreditOrderId, creditOrderId));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public CreditPackage findPackage(String id) {
        return packages.selectById(id);
    }

    @Override
    public CreditOrder findOrder(String id) {
        return orders.selectById(id);
    }

    @Override
    public CreditOrder findClientOrder(String userId, String clientOrderId) {
        return orders.selectOne(new LambdaQueryWrapper<CreditOrder>()
                .eq(CreditOrder::getUserId, userId)
                .eq(CreditOrder::getClientOrderId, clientOrderId)
                .last("LIMIT 1"));
    }

    @Override
    public void insertOrder(CreditOrder order) {
        orders.insert(order);
    }

    @Override
    public void updateOrder(CreditOrder order) {
        orders.updateById(order);
    }

    @Override
    public List<CreditPackage> activePackages() {
        return packages.selectList(new LambdaQueryWrapper<CreditPackage>()
                .eq(CreditPackage::getStatus, ActiveStatus.ACTIVE)
                .orderByAsc(CreditPackage::getSort));
    }

    @Override
    public List<CreditLedger> ledger(String userId) {
        return ledgers.selectList(new LambdaQueryWrapper<CreditLedger>()
                .eq(CreditLedger::getUserId, userId)
                .orderByDesc(CreditLedger::getCreatedAt));
    }
/**
 * 读可用积分。没有账户时按零返回。
 */

    @Override
    public BigDecimal available(String userId) {
        CreditAccount account = accounts.selectById(userId);
        return account == null || account.getAvailable() == null ? BigDecimal.ZERO : account.getAvailable();
    }
}
