package com.omino.shopai.app.order;

import com.omino.shopai.enums.Platform;
import com.omino.shopai.enums.WalletLedgerType;
import com.omino.shopai.app.service.order.OrderStore;
import com.omino.shopai.entity.AppOrder;
import com.omino.shopai.entity.OrderSyncCursor;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.WalletLedger;
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

final class MemoryOrders implements OrderStore, TransactionOperations {

    boolean failAdvance;

    private Map<String, AppOrder> committedOrders = new LinkedHashMap<>();
    private Map<String, Wallet> committedWallets = new LinkedHashMap<>();
    private List<WalletLedger> committedLedgers = new ArrayList<>();
    private Map<String, OrderSyncCursor> committedCursors = new LinkedHashMap<>();
    private Map<String, AppOrder> pendingOrders;
    private Map<String, Wallet> pendingWallets;
    private List<WalletLedger> pendingLedgers;
    private Map<String, OrderSyncCursor> pendingCursors;

    void seedWallet(String userId) {
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setAvailableAmount(new BigDecimal("0.00"));
        wallet.setFrozenAmount(new BigDecimal("0.00"));
        wallet.setTotalEarned(new BigDecimal("0.00"));
        wallet.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        wallets().put(userId, wallet);
    }

    List<WalletLedger> ledgers(String userId) {
        List<WalletLedger> rows = new ArrayList<>();
        for (WalletLedger ledger : ledgers()) {
            if (userId.equals(ledger.getUserId())) {
                rows.add(copy(ledger));
            }
        }
        return rows;
    }

    @Override
    public <T> T execute(TransactionCallback<T> action) {
        pendingOrders = copyOrders(committedOrders);
        pendingWallets = copyWallets(committedWallets);
        pendingLedgers = copyLedgers(committedLedgers);
        pendingCursors = copyCursors(committedCursors);
        try {
            TransactionStatus status = new SimpleTransactionStatus();
            T value = action.doInTransaction(status);
            committedOrders = pendingOrders;
            committedWallets = pendingWallets;
            committedLedgers = pendingLedgers;
            committedCursors = pendingCursors;
            return value;
        } catch (RuntimeException ex) {
            throw ex;
        } finally {
            pendingOrders = null;
            pendingWallets = null;
            pendingLedgers = null;
            pendingCursors = null;
        }
    }

    @Override
    public OrderSyncCursor cursor(String platform) {
        OrderSyncCursor stored = cursors().get(platform);
        if (stored == null) {
            OrderSyncCursor blank = new OrderSyncCursor();
            blank.setPlatform(Platform.fromCode(platform));
            return blank;
        }
        return copy(stored);
    }

    @Override
    public void advance(String platform, OffsetDateTime now) {
        OrderSyncCursor cursor = copy(cursor(platform));
        cursor.setLastSyncCreateTime(now);
        cursor.setLastSyncUpdateTime(now);
        cursor.setLastError(null);
        cursor.setUpdatedAt(now);
        cursors().put(platform, cursor);
        if (failAdvance) {
            throw new IllegalStateException("cursor");
        }
    }

    @Override
    public void fail(String platform, String message) {
        OrderSyncCursor cursor = copy(cursor(platform));
        if (cursor.getPlatform() == null) {
            cursor.setPlatform(Platform.fromCode(platform));
        }
        cursor.setLastError(message);
        cursor.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        cursors().put(platform, cursor);
    }

    @Override
    public AppOrder findByPlatformOrder(String platform, String platformOrderId) {
        for (AppOrder order : orders().values()) {
            if (platform.equals(order.getPlatform() == null ? null : order.getPlatform().code()) && platformOrderId.equals(order.getPlatformOrderId())) {
                return copy(order);
            }
        }
        return null;
    }

    @Override
    public void save(AppOrder order) {
        orders().put(order.getId(), copy(order));
    }

    @Override
    public Wallet lockWallet(String userId) {
        Wallet stored = wallets().get(userId);
        return stored == null ? null : copy(stored);
    }

    @Override
    public void saveWallet(Wallet wallet) {
        wallets().put(wallet.getUserId(), copy(wallet));
    }

    @Override
    public WalletLedger findCommissionByOrder(String orderId) {
        for (WalletLedger ledger : ledgers()) {
            if (WalletLedgerType.COMMISSION == ledger.getType() && orderId.equals(ledger.getOrderId())) {
                return copy(ledger);
            }
        }
        return null;
    }

    @Override
    public void insertLedger(WalletLedger ledger) {
        if (WalletLedgerType.COMMISSION == ledger.getType() && findCommissionByOrder(ledger.getOrderId()) != null) {
            throw new IllegalStateException("commission");
        }
        ledgers().add(copy(ledger));
    }

    @Override
    public int commissionCount(String userId) {
        return ledgers(userId).size() == 0 ? 0 : (int) ledgers().stream()
                .filter(ledger -> userId.equals(ledger.getUserId()) && WalletLedgerType.COMMISSION == ledger.getType())
                .count();
    }

    @Override
    public List<AppOrder> list(String userId, String platform, String orderStatus, String settleStatus, int page, int size) {
        List<AppOrder> rows = filtered(userId, platform, orderStatus, settleStatus);
        int from = Math.min((page - 1) * size, rows.size());
        int to = Math.min(from + size, rows.size());
        List<AppOrder> pageRows = new ArrayList<>();
        for (AppOrder row : rows.subList(from, to)) {
            pageRows.add(copy(row));
        }
        return pageRows;
    }

    @Override
    public long count(String userId, String platform, String orderStatus, String settleStatus) {
        return filtered(userId, platform, orderStatus, settleStatus).size();
    }

    @Override
    public AppOrder findById(String id) {
        AppOrder stored = orders().get(id);
        return stored == null ? null : copy(stored);
    }

    private List<AppOrder> filtered(String userId, String platform, String orderStatus, String settleStatus) {
        List<AppOrder> rows = new ArrayList<>();
        for (AppOrder order : orders().values()) {
            if (!userId.equals(order.getUserId())) {
                continue;
            }
            if (platform != null && !platform.equals(order.getPlatform() == null ? null : order.getPlatform().code())) {
                continue;
            }
            if (orderStatus != null && !orderStatus.equals(order.getOrderStatus() == null ? null : order.getOrderStatus().code())) {
                continue;
            }
            if (settleStatus != null && !settleStatus.equals(order.getSettleStatus() == null ? null : order.getSettleStatus().code())) {
                continue;
            }
            rows.add(order);
        }
        rows.sort(Comparator.comparing(AppOrder::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    private Map<String, AppOrder> orders() {
        return pendingOrders != null ? pendingOrders : committedOrders;
    }

    private Map<String, Wallet> wallets() {
        return pendingWallets != null ? pendingWallets : committedWallets;
    }

    private List<WalletLedger> ledgers() {
        return pendingLedgers != null ? pendingLedgers : committedLedgers;
    }

    private Map<String, OrderSyncCursor> cursors() {
        return pendingCursors != null ? pendingCursors : committedCursors;
    }

    private static Map<String, AppOrder> copyOrders(Map<String, AppOrder> source) {
        Map<String, AppOrder> copy = new LinkedHashMap<>();
        for (AppOrder order : source.values()) {
            copy.put(order.getId(), copy(order));
        }
        return copy;
    }

    private static Map<String, Wallet> copyWallets(Map<String, Wallet> source) {
        Map<String, Wallet> copy = new LinkedHashMap<>();
        for (Wallet wallet : source.values()) {
            copy.put(wallet.getUserId(), copy(wallet));
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

    private static Map<String, OrderSyncCursor> copyCursors(Map<String, OrderSyncCursor> source) {
        Map<String, OrderSyncCursor> copy = new LinkedHashMap<>();
        for (OrderSyncCursor cursor : source.values()) {
            copy.put(cursor.getPlatform() == null ? null : cursor.getPlatform().code(), copy(cursor));
        }
        return copy;
    }

    private static AppOrder copy(AppOrder source) {
        AppOrder copy = new AppOrder();
        copy.setId(source.getId());
        copy.setUserId(source.getUserId());
        copy.setPlatform(source.getPlatform());
        copy.setPlatformOrderId(source.getPlatformOrderId());
        copy.setGoodsId(source.getGoodsId());
        copy.setGoodsTitle(source.getGoodsTitle());
        copy.setGoodsImageUrl(source.getGoodsImageUrl());
        copy.setPayAmount(scale(source.getPayAmount()));
        copy.setParentUserId(source.getParentUserId());
        copy.setMediaCommission(scale(source.getMediaCommission()));
        copy.setUserCommission(scale(source.getUserCommission()));
        copy.setClickId(source.getClickId());
        copy.setOrderStatus(source.getOrderStatus());
        copy.setSettleStatus(source.getSettleStatus());
        copy.setWalletLedgerId(source.getWalletLedgerId());
        copy.setOrderedAt(source.getOrderedAt());
        copy.setShippedAt(source.getShippedAt());
        copy.setSettledAt(source.getSettledAt());
        copy.setUpdatedFromPlatformAt(source.getUpdatedFromPlatformAt());
        copy.setCreatedAt(source.getCreatedAt());
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

    private static OrderSyncCursor copy(OrderSyncCursor source) {
        OrderSyncCursor copy = new OrderSyncCursor();
        copy.setPlatform(source.getPlatform());
        copy.setLastSyncCreateTime(source.getLastSyncCreateTime());
        copy.setLastSyncUpdateTime(source.getLastSyncUpdateTime());
        copy.setLastError(source.getLastError());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
