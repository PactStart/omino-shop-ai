package com.omino.shopai.app.service.order;

import com.omino.shopai.app.adapter.platform.PlatformOrderSource;
import com.omino.shopai.dto.order.IncomingOrder;
import com.omino.shopai.enums.Platform;
import com.omino.shopai.enums.OrderStatus;
import com.omino.shopai.enums.SettleStatus;
import com.omino.shopai.enums.WalletLedgerType;
import com.omino.shopai.entity.AppOrder;
import com.omino.shopai.entity.OrderSyncCursor;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.WalletLedger;
import com.omino.shopai.support.Ids;
import com.omino.shopai.support.withdrawal.WalletMath;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
/**
 * OrderSyncService 编排用例，事务和校验放在这里。
 */

@Service
public class OrderSyncService {

    private static final Set<String> ORDER_STATUSES = Set.of(
            "pending_pay", "pending_ship", "shipped", "completed", "cancelled", "unknown");

    private final OrderStore store;
    private final PlatformOrderSource source;
    private final TransactionOperations tx;
    private final Clock clock;
    private final BigDecimal rate;
/**
 * 注入订单来源、事务和分成比例。比例必须在 0 到 1 之间。
 */

    public OrderSyncService(
            OrderStore store,
            PlatformOrderSource source,
            TransactionOperations tx,
            Clock clock,
            @Value("${commission.user-share-rate:0.80}") String shareRate) {
        this.store = store;
        this.source = source;
        this.tx = tx;
        this.clock = clock;
        this.rate = new BigDecimal(shareRate);
        if (this.rate.compareTo(BigDecimal.ZERO) <= 0 || this.rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("commission.user-share-rate");
        }
    }
/**
 * 整批订单和游标前进放在同一事务里，失败则下次重拉。
 */

    public void sync(String platform) {
        OrderSyncCursor cursor = store.cursor(platform);
        List<IncomingOrder> batch = source.pull(platform, cursor);
        tx.executeWithoutResult(status -> {
            // 本批写库和游标前进同一事务。中途失败则游标不动，下次重拉。
            for (IncomingOrder incoming : batch) {
                upsert(platform, incoming);
            }
            store.advance(platform, OffsetDateTime.now(clock));
        });
    }

    /**
     * 这一平台失败时只记固定文案。两列水位都不改。
     */
    public void noteFailure(String platform) {
        store.fail(platform, "上游失败");
    }
/**
 * 已入账的订单只更新快照。未入账且已完成的才记返佣。
 */

    private void upsert(String platform, IncomingOrder incoming) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        AppOrder existing = store.findByPlatformOrder(platform, incoming.platformOrderId());
        AppOrder order = existing == null ? newOrder(platform, incoming.platformOrderId(), now) : existing;
        boolean booked = existing != null && (existing.getWalletLedgerId() != null
                || store.findCommissionByOrder(existing.getId()) != null);
        BigDecimal bookedCommission = order.getUserCommission();
        String bookedLedger = order.getWalletLedgerId();
        OffsetDateTime bookedSettledAt = order.getSettledAt();

        order.setUserId(blankToNull(incoming.userId()));
        order.setParentUserId(blankToNull(incoming.parentUserId()));
        order.setGoodsId(blankToNull(incoming.goodsId()));
        order.setGoodsTitle(incoming.goodsTitle() == null || incoming.goodsTitle().isBlank() ? "商品" : incoming.goodsTitle());
        order.setGoodsImageUrl(incoming.goodsImageUrl());
        order.setPayAmount(incoming.payAmount());
        order.setMediaCommission(incoming.mediaCommission());
        order.setOrderStatus(orderStatus(incoming.orderStatus()));
        order.setOrderedAt(incoming.orderedAt());
        order.setShippedAt(incoming.shippedAt());
        order.setUpdatedFromPlatformAt(now);

        // 已经记过返佣的订单只更新商品快照，佣金和流水号保持第一次的值。
        if (booked) {
            order.setUserCommission(bookedCommission);
            order.setSettleStatus(SettleStatus.SETTLED);
            order.setWalletLedgerId(bookedLedger);
            order.setSettledAt(bookedSettledAt);
            store.save(order);
            return;
        }

        BigDecimal userCommission = incoming.mediaCommission() == null
                ? null
                : incoming.mediaCommission().multiply(rate).setScale(2, RoundingMode.HALF_UP);
        order.setUserCommission(userCommission);
        order.setSettleStatus(settleStatus(order));
        order.setWalletLedgerId(null);
        store.save(order);
        if (OrderStatus.COMPLETED == order.getOrderStatus()
                && SettleStatus.PENDING == order.getSettleStatus()
                && userCommission != null
                && userCommission.signum() > 0
                && store.findCommissionByOrder(order.getId()) == null) {
            credit(order, userCommission, now);
        }
    }
/**
 * 锁钱包后入账，并把流水号写回订单，防止下次再记。
 */

    private void credit(AppOrder order, BigDecimal amount, OffsetDateTime now) {
        // 锁住买家钱包再入账，并把流水号写回订单，下次同步看到就不再记一笔。
        Wallet wallet = store.lockWallet(order.getUserId());
        if (wallet == null) {
            wallet = new Wallet();
            wallet.setUserId(order.getUserId());
            wallet.setAvailableAmount(BigDecimal.ZERO.setScale(2));
            wallet.setFrozenAmount(BigDecimal.ZERO.setScale(2));
            wallet.setTotalEarned(BigDecimal.ZERO.setScale(2));
            wallet.setUpdatedAt(now);
        }
        WalletMath.credit(wallet, amount, now);
        store.saveWallet(wallet);
        WalletLedger ledger = new WalletLedger();
        ledger.setId(Ids.ulid());
        ledger.setUserId(order.getUserId());
        ledger.setType(WalletLedgerType.COMMISSION);
        ledger.setAmount(amount);
        ledger.setOrderId(order.getId());
        ledger.setCreatedAt(now);
        store.insertLedger(ledger);
        order.setWalletLedgerId(ledger.getId());
        order.setSettleStatus(SettleStatus.SETTLED);
        order.setSettledAt(now);
        store.save(order);
    }
/**
 * 按平台和平台订单号建本地行。平台写成枚举。
 */

    private static AppOrder newOrder(String platform, String platformOrderId, OffsetDateTime now) {
        AppOrder order = new AppOrder();
        order.setId(Ids.ulid());
        order.setPlatform(Platform.fromCode(platform));
        order.setPlatformOrderId(platformOrderId);
        order.setCreatedAt(now);
        return order;
    }
/**
 * 结算并保证同一单只入账一次。
 */

    private static SettleStatus settleStatus(AppOrder order) {
        if (order.getWalletLedgerId() != null) {
            return SettleStatus.SETTLED;
        }
        if (order.getUserId() == null) {
            return SettleStatus.NONE;
        }
        OrderStatus status = order.getOrderStatus();
        if (status == OrderStatus.PENDING_PAY || status == OrderStatus.CANCELLED || status == OrderStatus.UNKNOWN) {
            return SettleStatus.NONE;
        }
        if (order.getMediaCommission() == null || order.getMediaCommission().signum() <= 0) {
            return SettleStatus.NONE;
        }
        if (status == OrderStatus.PENDING_SHIP || status == OrderStatus.SHIPPED || status == OrderStatus.COMPLETED) {
            return SettleStatus.PENDING;
        }
        return SettleStatus.NONE;
    }

    private static OrderStatus orderStatus(String raw) {
        return raw != null && ORDER_STATUSES.contains(raw) ? OrderStatus.fromCode(raw) : OrderStatus.UNKNOWN;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
