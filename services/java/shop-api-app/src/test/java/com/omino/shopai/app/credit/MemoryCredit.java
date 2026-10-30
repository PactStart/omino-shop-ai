package com.omino.shopai.app.credit;

import com.omino.shopai.enums.ActiveStatus;
import com.omino.shopai.enums.CreditLedgerType;
import com.omino.shopai.app.service.credit.ICredit;
import com.omino.shopai.app.wallet.MemoryBooks;
import com.omino.shopai.entity.CreditAccount;
import com.omino.shopai.entity.CreditLedger;
import com.omino.shopai.entity.CreditOrder;
import com.omino.shopai.entity.CreditPackage;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.WalletLedger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class MemoryCredit implements ICredit {

    public final MemoryBooks books = new MemoryBooks();
    private final Map<String, CreditAccount> credits = new LinkedHashMap<>();
    private final List<CreditLedger> ledgers = new ArrayList<>();
    private final List<WalletLedger> walletLedgers = new ArrayList<>();
    private final Map<String, CreditPackage> packages = new LinkedHashMap<>();
    private final Map<String, CreditOrder> orders = new LinkedHashMap<>();

    public void seedWallet(String userId, String available) {
        books.seed(userId, available);
    }

    public void seedCredit(String userId, String available) {
        CreditAccount account = new CreditAccount();
        account.setUserId(userId);
        account.setAvailable(new BigDecimal(available).setScale(2, RoundingMode.HALF_UP));
        account.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        credits.put(userId, account);
    }

    public void savePackage(CreditPackage pack) {
        packages.put(pack.getId(), pack);
    }

    public List<WalletLedger> walletLedgers() {
        return List.copyOf(walletLedgers);
    }

    @Override
    public <T> T exclusively(String userId, Supplier<T> action) {
        return books.exclusively(userId, () -> {
            Map<String, CreditAccount> creditCopy = copyCredits();
            List<CreditLedger> ledgerCopy = new ArrayList<>(ledgers);
            List<WalletLedger> walletCopy = new ArrayList<>(walletLedgers);
            Map<String, CreditOrder> orderCopy = new LinkedHashMap<>(orders);
            try {
                return action.get();
            } catch (RuntimeException ex) {
                credits.clear();
                credits.putAll(creditCopy);
                ledgers.clear();
                ledgers.addAll(ledgerCopy);
                walletLedgers.clear();
                walletLedgers.addAll(walletCopy);
                orders.clear();
                orders.putAll(orderCopy);
                throw ex;
            }
        });
    }

    @Override
    public Wallet wallet(String userId) {
        return books.wallet(userId);
    }

    @Override
    public void saveWallet(Wallet wallet) {
        books.save(wallet);
    }

    @Override
    public void insertWalletLedger(WalletLedger ledger) {
        walletLedgers.add(ledger);
    }

    @Override
    public CreditAccount credit(String userId) {
        return credits.get(userId);
    }

    @Override
    public void saveCredit(CreditAccount account) {
        credits.put(account.getUserId(), account);
    }

    @Override
    public CreditPackage findPackage(String id) {
        return packages.get(id);
    }

    @Override
    public List<CreditPackage> activePackages() {
        return packages.values().stream().filter(pack -> ActiveStatus.ACTIVE == pack.getStatus()).toList();
    }

    @Override
    public CreditOrder findByClient(String userId, String clientOrderId) {
        for (CreditOrder order : orders.values()) {
            if (userId.equals(order.getUserId()) && clientOrderId.equals(order.getClientOrderId())) {
                return order;
            }
        }
        return null;
    }

    @Override
    public CreditOrder findOrder(String id) {
        return orders.get(id);
    }

    @Override
    public void saveOrder(CreditOrder order) {
        orders.put(order.getId(), order);
    }

    @Override
    public void insertLedger(CreditLedger ledger) {
        ledgers.add(ledger);
    }

    @Override
    public boolean purchaseExists(String creditOrderId) {
        return ledgers.stream().anyMatch(row -> CreditLedgerType.PURCHASE == row.getType() && creditOrderId.equals(row.getCreditOrderId()));
    }

    @Override
    public List<CreditLedger> ledger(String userId) {
        return ledgers.stream().filter(row -> userId.equals(row.getUserId())).toList();
    }

    private Map<String, CreditAccount> copyCredits() {
        Map<String, CreditAccount> copy = new LinkedHashMap<>();
        credits.forEach((id, account) -> {
            CreditAccount next = new CreditAccount();
            next.setUserId(account.getUserId());
            next.setAvailable(account.getAvailable());
            next.setUpdatedAt(account.getUpdatedAt());
            copy.put(id, next);
        });
        return copy;
    }
}
