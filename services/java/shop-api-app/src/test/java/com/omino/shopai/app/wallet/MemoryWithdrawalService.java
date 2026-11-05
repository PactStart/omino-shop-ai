package com.omino.shopai.app.wallet;

import com.omino.shopai.app.service.wallet.WithdrawalServiceImpl;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.Withdrawal;

import java.time.Clock;
import java.util.List;
import java.util.function.Supplier;

/**
 * 用内存账本替换提现用例里的表访问，业务方法仍走真实实现。
 */
final class MemoryWithdrawalService extends WithdrawalServiceImpl {

    private final MemoryBooks books;

    /**
     * 绑定同一份内存账本。表访问全部转给它。
     *
     * @param books 内存账本
     */
    MemoryWithdrawalService(MemoryBooks books) {
        super(null, null, null, null, Clock.systemUTC());
        this.books = books;
    }

    /**
     * 在内存锁里执行，保证并发申请看到同一份余额。
     *
     * @param userId 用户主键
     * @param action 锁内要做的事
     * @param <T> 锁内返回值
     * @return 锁内结果
     */
    @Override
    protected <T> T lock(String userId, Supplier<T> action) {
        return books.exclusively(userId, action);
    }

    /**
     * 从内存账本读取钱包。
     *
     * @param userId 用户主键
     * @return 钱包；没有则空
     */
    @Override
    protected Wallet loadWallet(String userId) {
        return books.wallet(userId);
    }

    /**
     * 把钱包写回内存账本。
     *
     * @param wallet 钱包
     */
    @Override
    protected void saveWallet(Wallet wallet) {
        books.save(wallet);
    }

    /**
     * 把提现单写入内存账本。
     *
     * @param withdrawal 提现单
     */
    @Override
    protected void insertWithdrawal(Withdrawal withdrawal) {
        books.insert(withdrawal);
    }

    /**
     * 从内存账本按主键读取提现单。
     *
     * @param id 提现单主键
     * @return 提现单；没有则空
     */
    @Override
    protected Withdrawal findWithdrawal(String id) {
        return books.find(id);
    }

    /**
     * 从内存账本列出一页提现单。
     *
     * @param userId 用户主键
     * @param page 页码，从 1 起
     * @param size 页大小
     * @return 当前页
     */
    @Override
    protected List<Withdrawal> listWithdrawals(String userId, int page, int size) {
        return books.list(userId, page, size);
    }

    /**
     * 数内存账本里该用户的提现单。
     *
     * @param userId 用户主键
     * @return 行数
     */
    @Override
    protected long countWithdrawals(String userId) {
        return books.count(userId);
    }
}
