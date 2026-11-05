package com.omino.shopai.app.service.wallet;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.dto.WalletViewDto;
import com.omino.shopai.app.dto.WithdrawalViewDto;
import com.omino.shopai.dto.PageDto;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.Withdrawal;
import com.omino.shopai.enums.PayoutChannel;
import com.omino.shopai.enums.WithdrawalStatus;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.WalletMapper;
import com.omino.shopai.mapper.WithdrawalMapper;
import com.omino.shopai.support.Ids;
import com.omino.shopai.support.withdrawal.WalletMath;
import com.omino.shopai.support.withdrawal.WithdrawalTransition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;

/**
 * 提现申请在锁住钱包行之后冻结余额。打款不在这里做。
 */
@Service
public class WithdrawalServiceImpl implements IWithdrawalService {

    private final WalletMapper wallets;
    private final WithdrawalMapper withdrawals;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    /**
     * 注入钱包、提现单和时钟。事务管理器只用于锁行。
     *
     * @param wallets 钱包表
     * @param withdrawals 提现单表
     * @param jdbc 行锁用的查询
     * @param manager 事务管理器
     * @param clock 冻结时间
     */
    public WithdrawalServiceImpl(
            WalletMapper wallets,
            WithdrawalMapper withdrawals,
            JdbcTemplate jdbc,
            PlatformTransactionManager manager,
            Clock clock) {
        this.wallets = wallets;
        this.withdrawals = withdrawals;
        this.jdbc = jdbc;
        this.tx = manager == null ? null : new TransactionTemplate(manager);
        this.clock = clock;
    }

    /**
     * 读取钱包余额。没有钱包时三个金额都是 0.00。
     *
     * @param userId 用户主键
     * @return 可用、冻结和累计收益
     */
    @Override
    public WalletViewDto wallet(String userId) {
        Wallet wallet = loadWallet(userId);
        if (wallet == null) {
            return new WalletViewDto("0.00", "0.00", "0.00");
        }
        return new WalletViewDto(money(wallet.getAvailableAmount()), money(wallet.getFrozenAmount()), money(wallet.getTotalEarned()));
    }

    /**
     * 申请提现并冻结可用余额。锁住该用户钱包行之后才改余额。
     *
     * @param userId 用户主键
     * @param amountText 金额文本，必须是正数且最多两位小数
     * @param payeeAccount 收款账号
     * @param channel 渠道；只接受支付宝，空则按支付宝
     * @return 新建的提现单
     */
    @Override
    public WithdrawalViewDto apply(String userId, String amountText, String payeeAccount, String channel) {
        WithdrawalTransition.requireAlipay(channel);
        BigDecimal amount = amount(amountText);
        if (payeeAccount == null || payeeAccount.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "收款账号不能为空");
        }
        String mask = mask(payeeAccount);
        return lock(userId, () -> {
            Wallet wallet = loadWallet(userId);
            if (wallet == null || wallet.getAvailableAmount().compareTo(amount) < 0) {
                throw new ApiBusinessException(ErrorCodes.VALIDATION, "金额不正确");
            }
            OffsetDateTime now = OffsetDateTime.now(clock);
            WalletMath.freeze(wallet, amount, now);
            saveWallet(wallet);
            Withdrawal row = new Withdrawal();
            row.setId(Ids.ulid());
            row.setUserId(userId);
            row.setAmount(amount);
            row.setStatus(WithdrawalStatus.PENDING);
            row.setChannel(PayoutChannel.ALIPAY);
            row.setPayeeAccount(payeeAccount);
            row.setAccountMask(mask);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            insertWithdrawal(row);
            return view(row);
        });
    }

    /**
     * 分页列出该用户的提现单。
     *
     * @param userId 用户主键
     * @param page 页码，从 1 起
     * @param size 页大小，最大 50
     * @return 提现单页
     */
    @Override
    public PageDto<WithdrawalViewDto> list(String userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        List<WithdrawalViewDto> rows = listWithdrawals(userId, safePage, safeSize).stream().map(WithdrawalServiceImpl::view).toList();
        return new PageDto<>(rows, safePage, safeSize, countWithdrawals(userId));
    }

    /**
     * 读取该用户的一笔提现。不属于该用户时按不存在处理。
     *
     * @param userId 用户主键
     * @param id 提现单主键
     * @return 提现单
     */
    @Override
    public WithdrawalViewDto get(String userId, String id) {
        Withdrawal row = findWithdrawal(id);
        if (row == null || !userId.equals(row.getUserId())) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "提现不存在");
        }
        return view(row);
    }

    /**
     * 锁住该用户钱包行之后执行。避免两笔申请同时把余额扣成负数。
     *
     * @param userId 用户主键
     * @param action 锁内要做的事
     * @param <T> 锁内返回值
     * @return 锁内结果
     */
    protected <T> T lock(String userId, Supplier<T> action) {
        return tx.execute(status -> {
            jdbc.queryForList("SELECT user_id FROM wallet WHERE user_id = ? FOR UPDATE", String.class, userId);
            return action.get();
        });
    }

    /**
     * 按主键读取钱包。
     *
     * @param userId 用户主键
     * @return 钱包；没有则空
     */
    protected Wallet loadWallet(String userId) {
        return wallets.selectById(userId);
    }

    /**
     * 没有钱包行就插入，已有则更新。
     *
     * @param wallet 钱包
     */
    protected void saveWallet(Wallet wallet) {
        if (wallets.selectById(wallet.getUserId()) == null) {
            wallets.insert(wallet);
        } else {
            wallets.updateById(wallet);
        }
    }

    /**
     * 插入一笔提现单。
     *
     * @param withdrawal 提现单
     */
    protected void insertWithdrawal(Withdrawal withdrawal) {
        withdrawals.insert(withdrawal);
    }

    /**
     * 按主键读取提现单。
     *
     * @param id 提现单主键
     * @return 提现单；没有则空
     */
    protected Withdrawal findWithdrawal(String id) {
        return withdrawals.selectById(id);
    }

    /**
     * 按创建时间倒序列出一页提现单。
     *
     * @param userId 用户主键
     * @param page 页码，从 1 起
     * @param size 页大小
     * @return 当前页
     */
    protected List<Withdrawal> listWithdrawals(String userId, int page, int size) {
        return withdrawals.selectList(new LambdaQueryWrapper<Withdrawal>()
                .eq(Withdrawal::getUserId, userId)
                .orderByDesc(Withdrawal::getCreatedAt)
                .last("LIMIT " + size + " OFFSET " + ((long) (page - 1) * size)));
    }

    /**
     * 数出该用户的提现单，供分页。
     *
     * @param userId 用户主键
     * @return 行数
     */
    protected long countWithdrawals(String userId) {
        Long count = withdrawals.selectCount(new LambdaQueryWrapper<Withdrawal>().eq(Withdrawal::getUserId, userId));
        return count == null ? 0 : count;
    }

    /**
     * 手机号只留前三后四。更短的账号只留首尾各两位。
     *
     * @param account 收款账号
     * @return 脱敏后的账号
     */
    static String mask(String account) {
        if (account != null && account.matches("^1[3-9]\\d{9}$")) {
            return account.substring(0, 3) + "****" + account.substring(account.length() - 4);
        }
        if (account == null || account.length() < 4) {
            return "****";
        }
        return account.substring(0, 2) + "****" + account.substring(account.length() - 2);
    }

    /**
     * 把提现单收成对外字段。时间用字符串。
     *
     * @param row 提现单
     * @return 对外提现单
     */
    private static WithdrawalViewDto view(Withdrawal row) {
        return new WithdrawalViewDto(
                row.getId(),
                money(row.getAmount()),
                row.getStatus() == null ? null : row.getStatus().code(),
                row.getChannel() == null ? null : row.getChannel().code(),
                row.getAccountMask(),
                row.getCreatedAt() == null ? null : row.getCreatedAt().toString());
    }

    /**
     * 把金额收成两位小数。非正数直接拒绝。
     *
     * @param text 金额文本
     * @return 两位小数的正数
     */
    private static BigDecimal amount(String text) {
        if (text == null || text.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "金额不正确");
        }
        try {
            BigDecimal value = new BigDecimal(text);
            if (value.scale() > 2 || value.signum() <= 0) {
                throw new ApiBusinessException(ErrorCodes.VALIDATION, "金额不正确");
            }
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "金额不正确");
        }
    }

    /**
     * 金额写成两位小数字符串。没有金额时保持空。
     *
     * @param value 金额，可空
     * @return 两位小数；空输入返回空
     */
    private static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
