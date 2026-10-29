package com.omino.shopai.app.wallet;

import com.omino.shopai.enums.WithdrawalStatus;
import com.omino.shopai.enums.WalletLedgerType;
import com.omino.shopai.app.service.wallet.WithdrawalStore;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.WalletLedger;
import com.omino.shopai.entity.Withdrawal;
import com.omino.shopai.support.Ids;
import com.omino.shopai.support.withdrawal.WithdrawalAccounts;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class MemoryBooks implements WithdrawalStore, WithdrawalAccounts, TransactionOperations {

    boolean rejectOnNextLockedRead;

    private final Map<String, Object> monitors = new ConcurrentHashMap<>();
    private Map<String, Wallet> committedWallets = new LinkedHashMap<>();
    private List<Withdrawal> committedWithdrawals = new ArrayList<>();
    private List<WalletLedger> committedLedgers = new ArrayList<>();
    private Map<String, Wallet> pendingWallets;
    private List<Withdrawal> pendingWithdrawals;
    private List<WalletLedger> pendingLedgers;
    private WithdrawalStatus pretendStatus;

    public void seed(String userId, String available) {
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setAvailableAmount(new BigDecimal(available).setScale(2, RoundingMode.HALF_UP));
        wallet.setFrozenAmount(new BigDecimal("0.00"));
        wallet.setTotalEarned(new BigDecimal("0.00"));
        wallet.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        wallets().put(userId, wallet);
    }

    void presetTransferId(String id, String value) {
        Withdrawal stored = stored(id);
        if (stored != null) {
            stored.setProviderTransferId(value);
        }
    }

    int ledgerCount() {
        return ledgers().size();
    }

    @Override
    public <T> T execute(TransactionCallback<T> action) {
        pendingWallets = copyWallets(committedWallets);
        pendingWithdrawals = copyWithdrawals(committedWithdrawals);
        pendingLedgers = copyLedgers(committedLedgers);
        try {
            TransactionStatus status = new SimpleTransactionStatus();
            T value = action.doInTransaction(status);
            committedWallets = pendingWallets;
            committedWithdrawals = pendingWithdrawals;
            committedLedgers = pendingLedgers;
            return value;
        } catch (RuntimeException ex) {
            throw ex;
        } finally {
            pendingWallets = null;
            pendingWithdrawals = null;
            pendingLedgers = null;
        }
    }

    @Override
    public <T> T exclusively(String userId, Supplier<T> action) {
        synchronized (monitor(userId)) {
            return execute(status -> action.get());
        }
    }

    @Override
    public void lock(String userId, Runnable action) {
        synchronized (monitor(userId)) {
            execute(status -> {
                if (rejectOnNextLockedRead) {
                    pretendStatus = WithdrawalStatus.REJECTED;
                    rejectOnNextLockedRead = false;
                }
                action.run();
                return null;
            });
        }
    }

    @Override
    public Wallet wallet(String userId) {
        Wallet stored = wallets().get(userId);
        return stored == null ? null : copy(stored);
    }

    @Override
    public void save(Wallet wallet) {
        wallets().put(wallet.getUserId(), copy(wallet));
    }

    @Override
    public void insert(Withdrawal withdrawal) {
        withdrawals().add(copy(withdrawal));
    }

    @Override
    public Withdrawal find(String id) {
        WithdrawalStatus override = pretendStatus;
        pretendStatus = null;
        Withdrawal stored = stored(id);
        if (stored == null) {
            return null;
        }
        Withdrawal copy = copy(stored);
        if (override != null) {
            copy.setStatus(override);
        }
        return copy;
    }

    @Override
    public void save(Withdrawal withdrawal) {
        List<Withdrawal> rows = withdrawals();
        for (int i = 0; i < rows.size(); i++) {
            if (withdrawal.getId().equals(rows.get(i).getId())) {
                rows.set(i, copy(withdrawal));
                return;
            }
        }
        rows.add(copy(withdrawal));
    }

    @Override
    public void addWithdrawLedger(Withdrawal withdrawal) {
        WalletLedger ledger = new WalletLedger();
        ledger.setId(Ids.ulid());
        ledger.setUserId(withdrawal.getUserId());
        ledger.setType(WalletLedgerType.WITHDRAW);
        ledger.setAmount(withdrawal.getAmount());
        ledger.setWithdrawalId(withdrawal.getId());
        ledger.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        ledgers().add(ledger);
    }

    @Override
    public int withdrawLedgerCount(String withdrawalId) {
        int count = 0;
        for (WalletLedger ledger : ledgers()) {
            if (WalletLedgerType.WITHDRAW == ledger.getType() && withdrawalId.equals(ledger.getWithdrawalId())) {
                count++;
            }
        }
        return count;
    }

    @Override
    public List<Withdrawal> list(String userId, int page, int size) {
        List<Withdrawal> rows = new ArrayList<>();
        for (Withdrawal row : withdrawals()) {
            if (userId.equals(row.getUserId())) {
                rows.add(row);
            }
        }
        rows.sort(Comparator.comparing(Withdrawal::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        int from = Math.min((page - 1) * size, rows.size());
        int to = Math.min(from + size, rows.size());
        List<Withdrawal> pageRows = new ArrayList<>();
        for (Withdrawal row : rows.subList(from, to)) {
            pageRows.add(copy(row));
        }
        return pageRows;
    }

    @Override
    public long count(String userId) {
        return withdrawals().stream().filter(row -> userId.equals(row.getUserId())).count();
    }

    private Withdrawal stored(String id) {
        for (Withdrawal row : withdrawals()) {
            if (id.equals(row.getId())) {
                return row;
            }
        }
        return null;
    }

    private Object monitor(String userId) {
        return monitors.computeIfAbsent(userId, key -> new Object());
    }

    private Map<String, Wallet> wallets() {
        return pendingWallets != null ? pendingWallets : committedWallets;
    }

    private List<Withdrawal> withdrawals() {
        return pendingWithdrawals != null ? pendingWithdrawals : committedWithdrawals;
    }

    private List<WalletLedger> ledgers() {
        return pendingLedgers != null ? pendingLedgers : committedLedgers;
    }

    private static Map<String, Wallet> copyWallets(Map<String, Wallet> source) {
        Map<String, Wallet> copy = new LinkedHashMap<>();
        for (Wallet wallet : source.values()) {
            copy.put(wallet.getUserId(), copy(wallet));
        }
        return copy;
    }

    private static List<Withdrawal> copyWithdrawals(List<Withdrawal> source) {
        List<Withdrawal> copy = new ArrayList<>();
        for (Withdrawal row : source) {
            copy.add(copy(row));
        }
        return copy;
    }

    private static List<WalletLedger> copyLedgers(List<WalletLedger> source) {
        List<WalletLedger> copy = new ArrayList<>();
        for (WalletLedger ledger : source) {
            copy.add(copy(ledger));
        }
        return copy;
    }

    private static Wallet copy(Wallet source) {
        Wallet copy = new Wallet();
        copy.setUserId(source.getUserId());
        copy.setAvailableAmount(scale(source.getAvailableAmount()));
        copy.setFrozenAmount(scale(source.getFrozenAmount()));
        copy.setTotalEarned(scale(source.getTotalEarned()));
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private static Withdrawal copy(Withdrawal source) {
        Withdrawal copy = new Withdrawal();
        copy.setId(source.getId());
        copy.setUserId(source.getUserId());
        copy.setAmount(scale(source.getAmount()));
        copy.setStatus(source.getStatus());
        copy.setChannel(source.getChannel());
        copy.setPayeeAccount(source.getPayeeAccount());
        copy.setAccountMask(source.getAccountMask());
        copy.setRejectReason(source.getRejectReason());
        copy.setProviderTransferId(source.getProviderTransferId());
        copy.setPaidAt(source.getPaidAt());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private static WalletLedger copy(WalletLedger source) {
        WalletLedger copy = new WalletLedger();
        copy.setId(source.getId());
        copy.setUserId(source.getUserId());
        copy.setType(source.getType());
        copy.setAmount(scale(source.getAmount()));
        copy.setOrderId(source.getOrderId());
        copy.setWithdrawalId(source.getWithdrawalId());
        copy.setCreditOrderId(source.getCreditOrderId());
        copy.setMemo(source.getMemo());
        copy.setCreatedAt(source.getCreatedAt());
        return copy;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
