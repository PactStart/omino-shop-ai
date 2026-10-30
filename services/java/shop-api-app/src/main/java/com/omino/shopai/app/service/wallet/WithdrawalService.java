package com.omino.shopai.app.service.wallet;

import com.omino.shopai.app.dto.PageDto;

import com.omino.shopai.app.dto.WithdrawalViewDto;

import com.omino.shopai.app.dto.WalletViewDto;

import com.omino.shopai.enums.WithdrawalStatus;
import com.omino.shopai.enums.PayoutChannel;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.Withdrawal;
import com.omino.shopai.support.Ids;
import com.omino.shopai.support.withdrawal.WalletMath;
import com.omino.shopai.support.withdrawal.WithdrawalTransition;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
/**
 * WithdrawalService 编排用例，事务和校验放在这里。
 */

@Service
public class WithdrawalService {

    private final IWithdrawal store;
    private final Clock clock;
/**
 * 注入账户与时钟。申请时冻结，不在这里打款。
 */

    public WithdrawalService(IWithdrawal store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }
/**
 * 取出用户钱包。没有则按零余额建一行，避免后续冻结空指针。
 */

    public WalletViewDto wallet(String userId) {
        Wallet wallet = store.wallet(userId);
        if (wallet == null) {
            return new WalletViewDto("0.00", "0.00", "0.00");
        }
        return new WalletViewDto(money(wallet.getAvailableAmount()), money(wallet.getFrozenAmount()), money(wallet.getTotalEarned()));
    }
/**
 * 提交申请。
 */

    public WithdrawalViewDto apply(String userId, String amountText, String payeeAccount, String channel) {
        WithdrawalTransition.requireAlipay(channel);
        BigDecimal amount = amount(amountText);
        if (payeeAccount == null || payeeAccount.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "收款账号不能为空");
        }
        String mask = mask(payeeAccount);
        return store.exclusively(userId, () -> {
            Wallet wallet = store.wallet(userId);
            if (wallet == null || wallet.getAvailableAmount().compareTo(amount) < 0) {
                throw new ApiBusinessException(ErrorCodes.VALIDATION, "金额不正确");
            }
            OffsetDateTime now = OffsetDateTime.now(clock);
            WalletMath.freeze(wallet, amount, now);
            store.save(wallet);
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
            store.insert(row);
            return view(row);
        });
    }
/**
 * 按条件返回列表。
 */

    public PageDto<WithdrawalViewDto> list(String userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        List<WithdrawalViewDto> rows = store.list(userId, safePage, safeSize).stream().map(WithdrawalService::view).toList();
        return new PageDto<WithdrawalViewDto>(rows, safePage, safeSize, store.count(userId));
    }
/**
 * 按标识读取。
 */

    public WithdrawalViewDto get(String userId, String id) {
        Withdrawal row = store.find(id);
        if (row == null || !userId.equals(row.getUserId())) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "提现不存在");
        }
        return view(row);
    }
/**
 * 脱敏后返回。
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

    private static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
