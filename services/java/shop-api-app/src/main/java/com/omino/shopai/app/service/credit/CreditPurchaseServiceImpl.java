package com.omino.shopai.app.service.credit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.CreditAccount;
import com.omino.shopai.entity.CreditLedger;
import com.omino.shopai.entity.CreditOrder;
import com.omino.shopai.entity.CreditPackage;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.WalletLedger;
import com.omino.shopai.enums.ActiveStatus;
import com.omino.shopai.enums.CreditLedgerType;
import com.omino.shopai.enums.CreditOrderStatus;
import com.omino.shopai.enums.PayChannel;
import com.omino.shopai.enums.WalletLedgerType;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.CreditAccountMapper;
import com.omino.shopai.mapper.CreditLedgerMapper;
import com.omino.shopai.mapper.CreditOrderMapper;
import com.omino.shopai.mapper.CreditPackageMapper;
import com.omino.shopai.mapper.WalletLedgerMapper;
import com.omino.shopai.mapper.WalletMapper;
import com.omino.shopai.support.Ids;
import com.omino.shopai.support.withdrawal.WalletMath;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 下单、钱包扣款和支付回调入账。同一用户的余额改动包在一把行锁里。
 */
@Service
public class CreditPurchaseServiceImpl implements ICreditPurchaseService {

    private static final Set<String> CHANNELS = Set.of("wechat_pay", "alipay", "wallet");

    private final WalletMapper wallets;
    private final WalletLedgerMapper walletLedgers;
    private final CreditAccountMapper credits;
    private final CreditLedgerMapper creditLedgers;
    private final CreditPackageMapper packages;
    private final CreditOrderMapper orders;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * 注入账户、订单、流水和行锁。下单与入账都走这里。
     *
     * @param wallets 返佣钱包
     * @param walletLedgers 钱包流水
     * @param credits 积分账户
     * @param creditLedgers 积分流水
     * @param packages 积分套餐
     * @param orders 积分订单
     * @param jdbc 行锁
     * @param transactions 入账事务
     * @param clock 下单和入账时间
     */
    public CreditPurchaseServiceImpl(
            WalletMapper wallets,
            WalletLedgerMapper walletLedgers,
            CreditAccountMapper credits,
            CreditLedgerMapper creditLedgers,
            CreditPackageMapper packages,
            CreditOrderMapper orders,
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            Clock clock) {
        this.wallets = wallets;
        this.walletLedgers = walletLedgers;
        this.credits = credits;
        this.creditLedgers = creditLedgers;
        this.packages = packages;
        this.orders = orders;
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * 创建积分订单。钱包渠道当场扣款入账，其它渠道保持待支付。
     *
     * @param userId 用户主键
     * @param packageId 套餐主键
     * @param channel 支付渠道
     * @param clientOrderId 客户端订单号，用来挡住重复下单
     * @return 已存在或新建的积分订单
     */
    @Override
    public CreditOrder place(String userId, String packageId, String channel, String clientOrderId) {
        if (!CHANNELS.contains(channel)) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "支付渠道不正确");
        }
        if (clientOrderId == null || clientOrderId.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "clientOrderId 不能为空");
        }
        CreditPackage pack = findPackage(packageId);
        if (pack == null || pack.getStatus() != ActiveStatus.ACTIVE) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "套餐不存在");
        }
        CreditOrder existing = findByClient(userId, clientOrderId);
        if (existing != null) {
            return existing;
        }
        if ("wallet".equals(channel)) {
            return exclusively(userId, () -> payByWallet(userId, pack, clientOrderId));
        }
        CreditOrder order = pending(userId, pack, channel, clientOrderId);
        saveOrder(order);
        return order;
    }

    /**
     * 支付回调入账，同一单只加一次积分。
     *
     * @param orderId 积分订单主键
     * @param txnId 支付流水号
     * @param verified 回调是否已验签
     * @return 验签通过且订单存在时为真；验签失败为假
     */
    @Override
    public boolean settle(String orderId, String txnId, boolean verified) {
        if (!verified) {
            return false;
        }
        CreditOrder found = loadOrder(orderId);
        if (found == null) {
            return false;
        }
        return exclusively(found.getUserId(), () -> creditOnce(found.getUserId(), orderId, txnId));
    }

    /**
     * 列出该用户的积分流水，新的在前。
     *
     * @param userId 用户主键
     * @return 积分流水
     */
    @Override
    public List<CreditLedger> ledger(String userId) {
        return loadLedger(userId);
    }

    /**
     * 按主键读取积分订单。不核对属主，调用方自行比对用户。
     *
     * @param id 积分订单主键
     * @return 积分订单；没有则空
     */
    @Override
    public CreditOrder findOrder(String id) {
        return loadOrder(id);
    }

    /**
     * 用返佣余额买积分。扣款、入账和两条流水在同一把用户锁里。
     *
     * @param userId 用户主键
     * @param pack 生效套餐
     * @param clientOrderId 客户端订单号
     * @return 已支付的积分订单
     */
    private CreditOrder payByWallet(String userId, CreditPackage pack, String clientOrderId) {
        // 锁内再查一次，避免并发把同一 clientOrderId 写成两笔已支付。
        CreditOrder existing = findByClient(userId, clientOrderId);
        if (existing != null) {
            return existing;
        }
        Wallet wallet = wallet(userId);
        BigDecimal price = money(pack.getPrice());
        if (wallet == null || wallet.getAvailableAmount().compareTo(price) < 0) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "余额不足");
        }
        // 扣款、积分入账和钱包流水在同一把锁里，任一步失败都一起回滚。
        WalletMath.spend(wallet, price, now());
        saveWallet(wallet);
        CreditOrder order = pending(userId, pack, "wallet", clientOrderId);
        order.setStatus(CreditOrderStatus.PAID);
        order.setPaidAt(now());
        saveOrder(order);
        addCredits(userId, pack.getCredits(), order.getId());
        WalletLedger ledger = new WalletLedger();
        ledger.setId(Ids.ulid());
        ledger.setUserId(userId);
        ledger.setType(WalletLedgerType.CREDIT_BUY);
        ledger.setAmount(price);
        ledger.setCreditOrderId(order.getId());
        ledger.setCreatedAt(now());
        insertWalletLedger(ledger);
        return order;
    }

    /**
     * 支付回调入账。已支付或已有购买流水则直接返回，重放不再加积分。
     *
     * @param userId 用户主键
     * @param orderId 积分订单主键
     * @param txnId 支付流水号
     * @return 订单存在时为真
     */
    private boolean creditOnce(String userId, String orderId, String txnId) {
        CreditOrder order = loadOrder(orderId);
        if (order == null) {
            return false;
        }
        // 回调重放停在这里，不再加一次积分。
        if (CreditOrderStatus.PAID == order.getStatus() || purchaseExists(orderId)) {
            return true;
        }
        order.setStatus(CreditOrderStatus.PAID);
        order.setPayTxnId(txnId);
        order.setPaidAt(now());
        order.setUpdatedAt(now());
        saveOrder(order);
        addCredits(userId, order.getCredits(), orderId);
        return true;
    }

    /**
     * 增加可用积分并写购买流水。同一订单已有流水则跳过。
     *
     * @param userId 用户主键
     * @param credits 增加的积分
     * @param orderId 积分订单主键
     */
    private void addCredits(String userId, BigDecimal credits, String orderId) {
        if (purchaseExists(orderId)) {
            return;
        }
        CreditAccount account = creditAccount(userId);
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
        saveCredit(account);
        CreditLedger ledger = new CreditLedger();
        ledger.setId(Ids.ulid());
        ledger.setUserId(userId);
        ledger.setType(CreditLedgerType.PURCHASE);
        ledger.setAmount(money(credits));
        ledger.setBalanceAfter(next);
        ledger.setCreditOrderId(orderId);
        ledger.setCreatedAt(now());
        insertLedger(ledger);
    }

    /**
     * 按套餐快照生成待支付订单，渠道写成枚举。
     *
     * @param userId 用户主键
     * @param pack 套餐
     * @param channel 支付渠道
     * @param clientOrderId 客户端订单号
     * @return 尚未落库的待支付订单
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

    /**
     * 取下单和入账使用的当前时间。
     *
     * @return UTC 时间
     */
    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /**
     * 金额收成两位小数。空值按零。
     *
     * @param value 金额，可空
     * @return 两位小数
     */
    static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 在事务里锁住该用户钱包行，再执行入账。
     *
     * @param userId 用户主键
     * @param action 锁内执行的动作
     * @param <T> 动作的返回类型
     * @return 动作的返回值
     */
    protected <T> T exclusively(String userId, Supplier<T> action) {
        return transactions.execute(status -> {
            jdbc.queryForList("SELECT user_id FROM wallet WHERE user_id = ? FOR UPDATE", String.class, userId);
            return action.get();
        });
    }

    /**
     * 读取返佣钱包。
     *
     * @param userId 用户主键
     * @return 钱包；没有则空
     */
    protected Wallet wallet(String userId) {
        return wallets.selectById(userId);
    }

    /**
     * 写回已修改的钱包。
     *
     * @param wallet 钱包
     */
    protected void saveWallet(Wallet wallet) {
        wallets.updateById(wallet);
    }

    /**
     * 追加一条钱包流水。
     *
     * @param ledger 钱包流水
     */
    protected void insertWalletLedger(WalletLedger ledger) {
        walletLedgers.insert(ledger);
    }

    /**
     * 读取积分账户。
     *
     * @param userId 用户主键
     * @return 积分账户；没有则空
     */
    protected CreditAccount creditAccount(String userId) {
        return credits.selectById(userId);
    }

    /**
     * 没有账户时插入，已有则更新。
     *
     * @param account 积分账户
     */
    protected void saveCredit(CreditAccount account) {
        if (credits.selectById(account.getUserId()) == null) {
            credits.insert(account);
        } else {
            credits.updateById(account);
        }
    }

    /**
     * 按主键读取套餐。
     *
     * @param id 套餐主键
     * @return 套餐；没有则空
     */
    protected CreditPackage findPackage(String id) {
        return packages.selectById(id);
    }

    /**
     * 按用户和客户端订单号查找已有订单。
     *
     * @param userId 用户主键
     * @param clientOrderId 客户端订单号
     * @return 积分订单；没有则空
     */
    protected CreditOrder findByClient(String userId, String clientOrderId) {
        return orders.selectOne(new LambdaQueryWrapper<CreditOrder>()
                .eq(CreditOrder::getUserId, userId)
                .eq(CreditOrder::getClientOrderId, clientOrderId));
    }

    /**
     * 按主键读取积分订单。
     *
     * @param id 积分订单主键
     * @return 积分订单；没有则空
     */
    protected CreditOrder loadOrder(String id) {
        return orders.selectById(id);
    }

    /**
     * 没有订单时插入，已有则更新。
     *
     * @param order 积分订单
     */
    protected void saveOrder(CreditOrder order) {
        if (orders.selectById(order.getId()) == null) {
            orders.insert(order);
        } else {
            orders.updateById(order);
        }
    }

    /**
     * 追加一条积分流水。
     *
     * @param ledger 积分流水
     */
    protected void insertLedger(CreditLedger ledger) {
        creditLedgers.insert(ledger);
    }

    /**
     * 该订单是否已有购买流水，用来挡住回调重放。
     *
     * @param creditOrderId 积分订单主键
     * @return 已有购买流水时为真
     */
    protected boolean purchaseExists(String creditOrderId) {
        Long count = creditLedgers.selectCount(new LambdaQueryWrapper<CreditLedger>()
                .eq(CreditLedger::getCreditOrderId, creditOrderId)
                .eq(CreditLedger::getType, CreditLedgerType.PURCHASE));
        return count != null && count > 0;
    }

    /**
     * 列出该用户的积分流水，新的在前。
     *
     * @param userId 用户主键
     * @return 积分流水
     */
    protected List<CreditLedger> loadLedger(String userId) {
        return creditLedgers.selectList(new LambdaQueryWrapper<CreditLedger>()
                .eq(CreditLedger::getUserId, userId)
                .orderByDesc(CreditLedger::getCreatedAt));
    }
}
