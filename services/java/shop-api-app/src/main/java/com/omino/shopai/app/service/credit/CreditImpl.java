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

import java.util.List;
import java.util.function.Supplier;
/**
 * 持久化边界。事务和状态判断由调用方负责。
 */

@Repository
public class CreditImpl implements ICredit {

    private final WalletMapper wallets;
    private final WalletLedgerMapper walletLedgers;
    private final CreditAccountMapper credits;
    private final CreditLedgerMapper creditLedgers;
    private final CreditPackageMapper packages;
    private final CreditOrderMapper orders;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
/**
 * 积分账户、订单和流水的 MyBatis 访问。
 */

    public CreditImpl(
            WalletMapper wallets,
            WalletLedgerMapper walletLedgers,
            CreditAccountMapper credits,
            CreditLedgerMapper creditLedgers,
            CreditPackageMapper packages,
            CreditOrderMapper orders,
            JdbcTemplate jdbc,
            TransactionTemplate transactions) {
        this.wallets = wallets;
        this.walletLedgers = walletLedgers;
        this.credits = credits;
        this.creditLedgers = creditLedgers;
        this.packages = packages;
        this.orders = orders;
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Override
    public <T> T exclusively(String userId, Supplier<T> action) {
        return transactions.execute(status -> {
            jdbc.queryForList("SELECT user_id FROM wallet WHERE user_id = ? FOR UPDATE", String.class, userId);
            return action.get();
        });
    }

    @Override
    public Wallet wallet(String userId) {
        return wallets.selectById(userId);
    }

    @Override
    public void saveWallet(Wallet wallet) {
        wallets.updateById(wallet);
    }

    @Override
    public void insertWalletLedger(WalletLedger ledger) {
        walletLedgers.insert(ledger);
    }

    @Override
    public CreditAccount credit(String userId) {
        return credits.selectById(userId);
    }
/**
 * 保存修改。
 */

    @Override
    public void saveCredit(CreditAccount account) {
        if (credits.selectById(account.getUserId()) == null) {
            credits.insert(account);
        } else {
            credits.updateById(account);
        }
    }

    @Override
    public CreditPackage findPackage(String id) {
        return packages.selectById(id);
    }

    @Override
    public List<CreditPackage> activePackages() {
        return packages.selectList(new LambdaQueryWrapper<CreditPackage>()
                .eq(CreditPackage::getStatus, ActiveStatus.ACTIVE)
                .orderByAsc(CreditPackage::getSort));
    }

    @Override
    public CreditOrder findByClient(String userId, String clientOrderId) {
        return orders.selectOne(new LambdaQueryWrapper<CreditOrder>()
                .eq(CreditOrder::getUserId, userId)
                .eq(CreditOrder::getClientOrderId, clientOrderId));
    }

    @Override
    public CreditOrder findOrder(String id) {
        return orders.selectById(id);
    }
/**
 * 保存修改。
 */

    @Override
    public void saveOrder(CreditOrder order) {
        if (orders.selectById(order.getId()) == null) {
            orders.insert(order);
        } else {
            orders.updateById(order);
        }
    }

    @Override
    public void insertLedger(CreditLedger ledger) {
        creditLedgers.insert(ledger);
    }
/**
 * 该订单是否已有购买流水，用来挡住回调重放。
 */

    @Override
    public boolean purchaseExists(String creditOrderId) {
        Long count = creditLedgers.selectCount(new LambdaQueryWrapper<CreditLedger>()
                .eq(CreditLedger::getCreditOrderId, creditOrderId)
                .eq(CreditLedger::getType, CreditLedgerType.PURCHASE));
        return count != null && count > 0;
    }

    @Override
    public List<CreditLedger> ledger(String userId) {
        return creditLedgers.selectList(new LambdaQueryWrapper<CreditLedger>()
                .eq(CreditLedger::getUserId, userId)
                .orderByDesc(CreditLedger::getCreatedAt));
    }
}
