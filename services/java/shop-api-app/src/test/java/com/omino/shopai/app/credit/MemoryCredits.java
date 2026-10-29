package com.omino.shopai.app.credit;

import com.omino.shopai.enums.ActiveStatus;
import com.omino.shopai.enums.CreditLedgerType;
import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.service.credit.CreditBook;
import com.omino.shopai.entity.CreditAccount;
import com.omino.shopai.entity.CreditLedger;
import com.omino.shopai.entity.CreditOrder;
import com.omino.shopai.entity.CreditPackage;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.WalletLedger;
import com.omino.shopai.support.Ids;
import com.omino.shopai.support.withdrawal.WalletMath;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class MemoryCredits implements CreditBook {

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

    @Override
    public <T> T exclusively(String userId, Supplier<T> action) {
        synchronized (locks.computeIfAbsent(userId, key -> new Object())) {
            return action.get();
        }
    }

    @Override
    public Wallet wallet(String userId) {
        return wallets.stream().filter(row -> userId.equals(row.getUserId())).findFirst().orElse(null);
    }

    @Override
    public void saveWallet(Wallet wallet) {
        if (wallet(wallet.getUserId()) == null) {
            wallets.add(wallet);
        }
    }

    @Override
    public void insertWalletLedger(WalletLedger ledger) {
        walletLedgers.add(ledger);
    }

    @Override
    public CreditAccount credit(String userId) {
        return accounts.stream().filter(row -> userId.equals(row.getUserId())).findFirst().orElse(null);
    }

    @Override
    public void saveCredit(CreditAccount account) {
        if (credit(account.getUserId()) == null) {
            accounts.add(account);
        }
    }

    @Override
    public CreditPackage findPackage(String id) {
        return packages.stream().filter(row -> id.equals(row.getId())).findFirst().orElse(null);
    }

    @Override
    public List<CreditPackage> activePackages() {
        return packages.stream().filter(row -> ActiveStatus.ACTIVE == row.getStatus()).toList();
    }

    @Override
    public CreditOrder findByClient(String userId, String clientOrderId) {
        return orders.stream()
                .filter(row -> userId.equals(row.getUserId()) && clientOrderId.equals(row.getClientOrderId()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public CreditOrder findOrder(String id) {
        return orders.stream().filter(row -> id.equals(row.getId())).findFirst().orElse(null);
    }

    @Override
    public void saveOrder(CreditOrder order) {
        if (findOrder(order.getId()) == null) {
            orders.add(order);
        }
    }

    @Override
    public void insertLedger(CreditLedger ledger) {
        creditLedgers.add(ledger);
    }

    @Override
    public boolean purchaseExists(String creditOrderId) {
        return creditLedgers.stream()
                .anyMatch(row -> CreditLedgerType.PURCHASE == row.getType() && creditOrderId.equals(row.getCreditOrderId()));
    }

    @Override
    public List<CreditLedger> ledger(String userId) {
        return creditLedgers.stream().filter(row -> userId.equals(row.getUserId())).toList();
    }
}
