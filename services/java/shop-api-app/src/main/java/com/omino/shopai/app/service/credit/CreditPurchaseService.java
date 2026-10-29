package com.omino.shopai.app.service.credit;

import com.omino.shopai.enums.PayChannel;
import com.omino.shopai.enums.CreditOrderStatus;
import com.omino.shopai.enums.ActiveStatus;
import com.omino.shopai.enums.WalletLedgerType;
import com.omino.shopai.enums.CreditLedgerType;
import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.CreditAccount;
import com.omino.shopai.entity.CreditLedger;
import com.omino.shopai.entity.CreditOrder;
import com.omino.shopai.entity.CreditPackage;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.WalletLedger;
import com.omino.shopai.support.Ids;
import com.omino.shopai.support.withdrawal.WalletMath;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
/**
 * CreditPurchaseService 编排用例，事务和校验放在这里。
 */

@Service
public class CreditPurchaseService {

    private static final Set<String> CHANNELS = Set.of("wechat_pay", "alipay", "wallet");

    private final CreditBook book;
    private final Clock clock;
/**
 * 注入账本和时钟。下单与入账都走这里。
 */

    public CreditPurchaseService(CreditBook book, Clock clock) {
        this.book = book;
        this.clock = clock;
    }
/**
 * 创建订单。
 */

    public CreditOrder place(String userId, String packageId, String channel, String clientOrderId) {
        if (!CHANNELS.contains(channel)) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "支付渠道不正确");
        }
        if (clientOrderId == null || clientOrderId.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "clientOrderId 不能为空");
        }
        CreditPackage pack = book.findPackage(packageId);
        if (pack == null || pack.getStatus() != ActiveStatus.ACTIVE) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "套餐不存在");
        }
        CreditOrder existing = book.findByClient(userId, clientOrderId);
        if (existing != null) {
            return existing;
        }
        if ("wallet".equals(channel)) {
            return book.exclusively(userId, () -> payByWallet(userId, pack, clientOrderId));
        }
        CreditOrder order = pending(userId, pack, channel, clientOrderId);
        book.saveOrder(order);
        return order;
    }
/**
 * 结算并保证同一单只入账一次。
 */

    public boolean settle(String orderId, String txnId, boolean verified) {
        if (!verified) {
            return false;
        }
        CreditOrder found = book.findOrder(orderId);
        if (found == null) {
            return false;
        }
        return book.exclusively(found.getUserId(), () -> creditOnce(found.getUserId(), orderId, txnId));
    }
/**
 * 用返佣余额买积分。扣款、入账和两条流水在同一把用户锁里。
 */

    private CreditOrder payByWallet(String userId, CreditPackage pack, String clientOrderId) {
        // 锁内再查一次，避免并发把同一 clientOrderId 写成两笔已支付。
        CreditOrder existing = book.findByClient(userId, clientOrderId);
        if (existing != null) {
            return existing;
        }
        Wallet wallet = book.wallet(userId);
        BigDecimal price = money(pack.getPrice());
        if (wallet == null || wallet.getAvailableAmount().compareTo(price) < 0) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "余额不足");
        }
        // 扣款、积分入账和钱包流水在同一把锁里，任一步失败都一起回滚。
        WalletMath.spend(wallet, price, now());
        book.saveWallet(wallet);
        CreditOrder order = pending(userId, pack, "wallet", clientOrderId);
        order.setStatus(CreditOrderStatus.PAID);
        order.setPaidAt(now());
        book.saveOrder(order);
        addCredits(userId, pack.getCredits(), order.getId());
        WalletLedger ledger = new WalletLedger();
        ledger.setId(Ids.ulid());
        ledger.setUserId(userId);
        ledger.setType(WalletLedgerType.CREDIT_BUY);
        ledger.setAmount(price);
        ledger.setCreditOrderId(order.getId());
        ledger.setCreatedAt(now());
        book.insertWalletLedger(ledger);
        return order;
    }
/**
 * 支付回调入账。已支付或已有购买流水则直接返回，重放不再加积分。
 */

    private boolean creditOnce(String userId, String orderId, String txnId) {
        CreditOrder order = book.findOrder(orderId);
        if (order == null) {
            return false;
        }
        // 回调重放停在这里，不再加一次积分。
        if (CreditOrderStatus.PAID == order.getStatus() || book.purchaseExists(orderId)) {
            return true;
        }
        order.setStatus(CreditOrderStatus.PAID);
        order.setPayTxnId(txnId);
        order.setPaidAt(now());
        order.setUpdatedAt(now());
        book.saveOrder(order);
        addCredits(userId, order.getCredits(), orderId);
        return true;
    }
/**
 * 增加可用积分并写购买流水。同一订单已有流水则跳过。
 */

    private void addCredits(String userId, BigDecimal credits, String orderId) {
        if (book.purchaseExists(orderId)) {
            return;
        }
        CreditAccount account = book.credit(userId);
        if (account == null) {
            account = new CreditAccount();
            account.setUserId(userId);
            account.setAvailable(money(BigDecimal.ZERO));
        }
        BigDecimal next = money(account.getAvailable()).add(money(credits));
        if (next.signum() < 0) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "积分不能为负");
        }
        account.setAvailable(next);
        account.setUpdatedAt(now());
        book.saveCredit(account);
        CreditLedger ledger = new CreditLedger();
        ledger.setId(Ids.ulid());
        ledger.setUserId(userId);
        ledger.setType(CreditLedgerType.PURCHASE);
        ledger.setAmount(money(credits));
        ledger.setBalanceAfter(next);
        ledger.setCreditOrderId(orderId);
        ledger.setCreatedAt(now());
        book.insertLedger(ledger);
    }
/**
 * 按套餐快照生成待支付订单，渠道写成枚举。
 */

    private CreditOrder pending(String userId, CreditPackage pack, String channel, String clientOrderId) {
        CreditOrder order = new CreditOrder();
        order.setId(Ids.ulid());
        order.setUserId(userId);
        order.setPackageId(pack.getId());
        order.setCredits(money(pack.getCredits()));
        order.setPayAmount(money(pack.getPrice()));
        order.setChannel(PayChannel.fromCode(channel));
        order.setStatus(CreditOrderStatus.PENDING);
        order.setClientOrderId(clientOrderId);
        order.setCreatedAt(now());
        order.setUpdatedAt(now());
        return order;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
