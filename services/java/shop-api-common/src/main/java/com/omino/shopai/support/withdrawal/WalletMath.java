package com.omino.shopai.support.withdrawal;

import com.omino.shopai.entity.Wallet;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
/**
 * 钱包加减。任一余额为负都让事务回滚。
 */

public final class WalletMath {

    private WalletMath() {
    }
/**
 * 把返佣加进可用和累计。结果不允许为负。
 */

    public static void credit(Wallet wallet, BigDecimal amount, OffsetDateTime now) {
        requirePositive(amount);
        wallet.setAvailableAmount(nonNegative(wallet.getAvailableAmount().add(amount)));
        wallet.setTotalEarned(nonNegative(wallet.getTotalEarned().add(amount)));
        wallet.setUpdatedAt(now);
    }
/**
 * 从可用划到冻结。可用不够则拒绝申请。
 */

    public static void freeze(Wallet wallet, BigDecimal amount, OffsetDateTime now) {
        requirePositive(amount);
        wallet.setAvailableAmount(nonNegative(wallet.getAvailableAmount().subtract(amount)));
        wallet.setFrozenAmount(nonNegative(wallet.getFrozenAmount().add(amount)));
        wallet.setUpdatedAt(now);
    }
/**
 * 拒绝提现时把冻结划回可用。
 */

    public static void unfreeze(Wallet wallet, BigDecimal amount, OffsetDateTime now) {
        requirePositive(amount);
        wallet.setFrozenAmount(nonNegative(wallet.getFrozenAmount().subtract(amount)));
        wallet.setAvailableAmount(nonNegative(wallet.getAvailableAmount().add(amount)));
        wallet.setUpdatedAt(now);
    }
/**
 * 打款成功后只减冻结，不再动可用。
 */

    public static void deductFrozen(Wallet wallet, BigDecimal amount, OffsetDateTime now) {
        requirePositive(amount);
        wallet.setFrozenAmount(nonNegative(wallet.getFrozenAmount().subtract(amount)));
        wallet.setUpdatedAt(now);
    }
/**
 * 用可用余额买积分。减完不允许为负。
 */

    public static void spend(Wallet wallet, BigDecimal amount, OffsetDateTime now) {
        requirePositive(amount);
        wallet.setAvailableAmount(nonNegative(wallet.getAvailableAmount().subtract(amount)));
        wallet.setUpdatedAt(now);
    }
/**
 * 金额必须大于零，否则这次变动没有意义。
 */

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalStateException("金额必须为正");
        }
    }
/**
 * 任一余额为负都是记账错误，直接抛出让事务回滚。
 */

    private static BigDecimal nonNegative(BigDecimal value) {
        if (value.signum() < 0) {
            throw new IllegalStateException("余额不能为负");
        }
        return value;
    }
}
