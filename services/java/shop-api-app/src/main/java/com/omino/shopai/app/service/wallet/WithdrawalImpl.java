package com.omino.shopai.app.service.wallet;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.Withdrawal;
import com.omino.shopai.mapper.WalletMapper;
import com.omino.shopai.mapper.WithdrawalMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.Supplier;
/**
 * 持久化边界。事务和状态判断由调用方负责。
 */

@Repository
public class WithdrawalImpl implements IWithdrawal {

    private final WalletMapper wallets;
    private final WithdrawalMapper withdrawals;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
/**
 * 提现单和钱包的 MyBatis 访问。状态机不放这里。
 */

    public WithdrawalImpl(
            WalletMapper wallets,
            WithdrawalMapper withdrawals,
            JdbcTemplate jdbc,
            PlatformTransactionManager manager) {
        this.wallets = wallets;
        this.withdrawals = withdrawals;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(manager);
    }

    @Override
    public <T> T exclusively(String userId, Supplier<T> action) {
        return tx.execute(status -> {
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
    public void save(Wallet wallet) {
        if (wallets.selectById(wallet.getUserId()) == null) {
            wallets.insert(wallet);
        } else {
            wallets.updateById(wallet);
        }
    }

    @Override
    public void insert(Withdrawal withdrawal) {
        withdrawals.insert(withdrawal);
    }

    @Override
    public Withdrawal find(String id) {
        return withdrawals.selectById(id);
    }

    @Override
    public List<Withdrawal> list(String userId, int page, int size) {
        return withdrawals.selectList(new LambdaQueryWrapper<Withdrawal>()
                .eq(Withdrawal::getUserId, userId)
                .orderByDesc(Withdrawal::getCreatedAt)
                .last("LIMIT " + size + " OFFSET " + ((long) (page - 1) * size)));
    }
/**
 * 数出用户的提现单，供分页。
 */

    @Override
    public long count(String userId) {
        Long count = withdrawals.selectCount(new LambdaQueryWrapper<Withdrawal>().eq(Withdrawal::getUserId, userId));
        return count == null ? 0 : count;
    }
}
