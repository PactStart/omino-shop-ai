package com.omino.shopai.app.credit;

import com.omino.shopai.enums.ActiveStatus;
import com.omino.shopai.enums.CreditLedgerType;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.service.chat.IChatEventService;
import com.omino.shopai.app.service.credit.CreditBillingImpl;
import com.omino.shopai.app.service.credit.CreditPurchaseServiceImpl;
import com.omino.shopai.entity.CreditAccount;
import com.omino.shopai.entity.CreditLedger;
import com.omino.shopai.entity.CreditOrder;
import com.omino.shopai.entity.CreditPackage;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.WalletLedger;
import com.omino.shopai.support.Ids;
import com.omino.shopai.support.withdrawal.WalletMath;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class MemoryCredits {

    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final List<Wallet> wallets = new ArrayList<>();
    private final List<WalletLedger> walletLedgers = new ArrayList<>();
    private final List<CreditAccount> accounts = new ArrayList<>();
    private final List<CreditLedger> creditLedgers = new ArrayList<>();
    private final List<CreditOrder> orders = new ArrayList<>();
    private final List<CreditPackage> packages = new ArrayList<>();

    void seed(String userId, String available) {
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setAvailableAmount(new BigDecimal(available));
        wallet.setFrozenAmount(BigDecimal.ZERO);
        wallet.setTotalEarned(BigDecimal.ZERO);
        wallet.setUpdatedAt(OffsetDateTime.now());
        wallets.add(wallet);
    }

    CreditPackage addPackage(String price, String credits) {
        CreditPackage pack = new CreditPackage();
        pack.setId(Ids.ulid());
        pack.setName("积分");
        pack.setPrice(new BigDecimal(price));
        pack.setCredits(new BigDecimal(credits));
        pack.setSort(0);
        pack.setStatus(ActiveStatus.ACTIVE);
        pack.setCreatedAt(OffsetDateTime.now());
        pack.setUpdatedAt(OffsetDateTime.now());
        packages.add(pack);
        return pack;
    }

    void freeze(String userId, String amount) {
        exclusively(userId, () -> {
            Wallet wallet = wallet(userId);
            BigDecimal value = new BigDecimal(amount);
            if (wallet == null || wallet.getAvailableAmount().compareTo(value) < 0) {
                throw new ApiBusinessException(ErrorCodes.VALIDATION, "金额不正确");
            }
            WalletMath.freeze(wallet, value, OffsetDateTime.now());
            return null;
        });
    }

    int walletLedgerCount(String type) {
        return (int) walletLedgers.stream().filter(row -> type.equals(row.getType() == null ? null : row.getType().code())).count();
    }

    BigDecimal available(String userId) {
        CreditAccount account = credit(userId);
        return account == null || account.getAvailable() == null ? BigDecimal.ZERO : account.getAvailable();
    }

    public <T> T exclusively(String userId, Supplier<T> action) {
        synchronized (locks.computeIfAbsent(userId, key -> new Object())) {
            return action.get();
        }
    }

    public Wallet wallet(String userId) {
        return wallets.stream().filter(row -> userId.equals(row.getUserId())).findFirst().orElse(null);
    }

    public void saveWallet(Wallet wallet) {
        if (wallet(wallet.getUserId()) == null) {
            wallets.add(wallet);
        }
    }

    public void insertWalletLedger(WalletLedger ledger) {
        walletLedgers.add(ledger);
    }

    public CreditAccount credit(String userId) {
        return accounts.stream().filter(row -> userId.equals(row.getUserId())).findFirst().orElse(null);
    }

    public void saveCredit(CreditAccount account) {
        if (credit(account.getUserId()) == null) {
            accounts.add(account);
        }
    }

    public CreditPackage findPackage(String id) {
        return packages.stream().filter(row -> id.equals(row.getId())).findFirst().orElse(null);
    }

    public List<CreditPackage> activePackages() {
        return packages.stream().filter(row -> ActiveStatus.ACTIVE == row.getStatus()).toList();
    }

    public CreditOrder findByClient(String userId, String clientOrderId) {
        return orders.stream()
                .filter(row -> userId.equals(row.getUserId()) && clientOrderId.equals(row.getClientOrderId()))
                .findFirst()
                .orElse(null);
    }

    public CreditOrder findOrder(String id) {
        return orders.stream().filter(row -> id.equals(row.getId())).findFirst().orElse(null);
    }

    public void saveOrder(CreditOrder order) {
        if (findOrder(order.getId()) == null) {
            orders.add(order);
        }
    }

    public void insertLedger(CreditLedger ledger) {
        creditLedgers.add(ledger);
    }

    public boolean purchaseExists(String creditOrderId) {
        return creditLedgers.stream()
                .anyMatch(row -> CreditLedgerType.PURCHASE == row.getType() && creditOrderId.equals(row.getCreditOrderId()));
    }

    public List<CreditLedger> ledger(String userId) {
        return creditLedgers.stream().filter(row -> userId.equals(row.getUserId())).toList();
    }
}

/**
 * 购积分仍走真实下单和入账，表访问换成内存账本。
 */
final class MemoryPurchase extends CreditPurchaseServiceImpl {

    private final MemoryCredits books;

    /**
     * 绑定同一份内存账本。
     *
     * @param books 内存账本
     * @param clock 下单时间
     */
    MemoryPurchase(MemoryCredits books, Clock clock) {
        super(null, null, null, null, null, null, null, null, clock);
        this.books = books;
    }

    /**
     * 在内存锁里执行入账。
     *
     * @param userId 用户主键
     * @param action 锁内执行的动作
     * @param <T> 动作的返回类型
     * @return 动作的返回值
     */
    @Override
    protected <T> T exclusively(String userId, Supplier<T> action) {
        return books.exclusively(userId, action);
    }

    /**
     * 从内存账本读取钱包。
     *
     * @param userId 用户主键
     * @return 钱包；没有则空
     */
    @Override
    protected Wallet wallet(String userId) {
        return books.wallet(userId);
    }

    /**
     * 把钱包写回内存账本。
     *
     * @param wallet 钱包
     */
    @Override
    protected void saveWallet(Wallet wallet) {
        books.saveWallet(wallet);
    }

    /**
     * 把钱包流水追加进内存账本。
     *
     * @param ledger 钱包流水
     */
    @Override
    protected void insertWalletLedger(WalletLedger ledger) {
        books.insertWalletLedger(ledger);
    }

    /**
     * 从内存账本读取积分账户。
     *
     * @param userId 用户主键
     * @return 积分账户；没有则空
     */
    @Override
    protected CreditAccount creditAccount(String userId) {
        return books.credit(userId);
    }

    /**
     * 把积分账户写回内存账本。
     *
     * @param account 积分账户
     */
    @Override
    protected void saveCredit(CreditAccount account) {
        books.saveCredit(account);
    }

    /**
     * 从内存账本读取套餐。
     *
     * @param id 套餐主键
     * @return 套餐；没有则空
     */
    @Override
    protected CreditPackage findPackage(String id) {
        return books.findPackage(id);
    }

    /**
     * 按客户端订单号从内存账本查找。
     *
     * @param userId 用户主键
     * @param clientOrderId 客户端订单号
     * @return 积分订单；没有则空
     */
    @Override
    protected CreditOrder findByClient(String userId, String clientOrderId) {
        return books.findByClient(userId, clientOrderId);
    }

    /**
     * 按主键从内存账本读取订单。
     *
     * @param id 积分订单主键
     * @return 积分订单；没有则空
     */
    @Override
    protected CreditOrder loadOrder(String id) {
        return books.findOrder(id);
    }

    /**
     * 把积分订单写回内存账本。
     *
     * @param order 积分订单
     */
    @Override
    protected void saveOrder(CreditOrder order) {
        books.saveOrder(order);
    }

    /**
     * 把积分流水追加进内存账本。
     *
     * @param ledger 积分流水
     */
    @Override
    protected void insertLedger(CreditLedger ledger) {
        books.insertLedger(ledger);
    }

    /**
     * 内存账本里是否已有购买流水。
     *
     * @param creditOrderId 积分订单主键
     * @return 已有购买流水时为真
     */
    @Override
    protected boolean purchaseExists(String creditOrderId) {
        return books.purchaseExists(creditOrderId);
    }

    /**
     * 从内存账本列出积分流水。
     *
     * @param userId 用户主键
     * @return 积分流水
     */
    @Override
    protected List<CreditLedger> loadLedger(String userId) {
        return books.ledger(userId);
    }
}

/**
 * 余额判断仍走真实扣费门，账户和套餐从内存账本读。
 */
final class MemoryBilling extends CreditBillingImpl {

    private final MemoryCredits books;

    /**
     * 绑定内存账本和事件流。
     *
     * @param books 内存账本
     * @param events 事件流
     * @param enforce 是否在余额不足时拦住开 run
     */
    MemoryBilling(MemoryCredits books, IChatEventService events, boolean enforce) {
        super(null, null, events, enforce, "1000");
        this.books = books;
    }

    /**
     * 从内存账本读取积分账户。
     *
     * @param userId 用户主键
     * @return 积分账户；没有则空
     */
    @Override
    protected CreditAccount loadAccount(String userId) {
        return books.credit(userId);
    }

    /**
     * 从内存账本列出生效套餐。
     *
     * @return 生效套餐
     */
    @Override
    protected List<CreditPackage> loadPackages() {
        return books.activePackages();
    }
}
