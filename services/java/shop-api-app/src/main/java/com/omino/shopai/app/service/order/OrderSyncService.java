package com.omino.shopai.app.service.order;

import com.omino.shopai.app.adapter.platform.IPlatformOrder;
import com.omino.shopai.app.util.SdkLog;
import com.omino.shopai.app.util.UnionMaps;
import com.omino.shopai.dto.order.IncomingOrderDto;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
/**
 * OrderSyncService 编排用例，事务和校验放在这里。
 */

@Service
public class OrderSyncService {

    private final IOrder store;
    private final IPlatformOrder source;
    private final TransactionOperations tx;
    private final Clock clock;
    private final Executor executor;
    private final BigDecimal rate;
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    /**
     * 测试用。追赶在调用线程里做完。
     *
     * @param store 订单与游标
     * @param source 平台订单
     * @param tx 事务
     * @param clock 本次请求时间
     * @param shareRate 用户分成比例
     */
    public OrderSyncService(
            IOrder store,
            IPlatformOrder source,
            TransactionOperations tx,
            Clock clock,
            String shareRate) {
        this(store, source, tx, clock, shareRate, Runnable::run);
    }

    /**
     * 注入订单来源、事务、分成比例和追赶线程。比例必须在 0 到 1 之间。
     *
     * @param store 订单与游标
     * @param source 平台订单
     * @param tx 事务
     * @param clock 本次请求时间
     * @param shareRate 用户分成比例
     * @param orderSyncExecutor 异步追赶用的线程
     */
    @Autowired
    public OrderSyncService(
            IOrder store,
            IPlatformOrder source,
            TransactionOperations tx,
            Clock clock,
            @Value("${commission.user-share-rate:0.80}") String shareRate,
            @Qualifier("orderSyncExecutor") Executor orderSyncExecutor) {
        this.store = store;
        this.source = source;
        this.tx = tx;
        this.clock = clock;
        this.executor = orderSyncExecutor;
        this.rate = new BigDecimal(shareRate);
        if (this.rate.compareTo(BigDecimal.ZERO) <= 0 || this.rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("commission.user-share-rate");
        }
    }

    /**
     * 受理一次追赶并立刻返回。同一平台已在追赶时不再并行一轮。失败记下这次的原因。
     *
     * @param platform 平台码
     */
    public void submit(String platform) {
        if (!running.add(platform)) {
            return;
        }
        executor.execute(() -> {
            try {
                sync(platform);
            } catch (RuntimeException ex) {
                noteFailure(platform, ex);
            } finally {
                running.remove(platform);
            }
        });
    }

    /**
     * 按平台允许的跨度逐段拉到本次请求时间。一段的订单和水位前进在同一事务里，失败则这段不前进。
     *
     * @param platform 平台码
     */
    public void sync(String platform) {
        OffsetDateTime requestedAt = OffsetDateTime.now(clock);
        Duration span = UnionMaps.maxSpan(platform);
        while (true) {
            OrderSyncCursor cursor = store.cursor(platform);
            UnionMaps.Window created = UnionMaps.slice(cursor.getLastSyncCreateTime(), requestedAt, span);
            UnionMaps.Window updated = UnionMaps.slice(cursor.getLastSyncUpdateTime(), requestedAt, span);
            if (created == null && updated == null) {
                return;
            }
            List<IncomingOrderDto> batch = source.pull(platform, cursor, created, updated);
            // 本段写库和水位前进同一事务。中途失败则这段水位不动，下次从这里重拉。
            tx.executeWithoutResult(status -> {
                for (IncomingOrderDto incoming : batch) {
                    upsert(platform, incoming);
                }
                store.advance(
                        platform,
                        created == null ? null : created.end(),
                        updated == null ? null : updated.end());
            });
        }
    }

    /**
     * 这一平台失败时记下原因。两列水位都不改。
     *
     * @param platform 平台码
     * @param error 这次失败
     */
    public void noteFailure(String platform, Throwable error) {
        store.fail(platform, reason(error));
    }

    /**
     * 取最内层的失败说明，去掉密钥，并截到游标列能放下的长度。
     *
     * @param error 这次失败
     * @return 可写入 last_error 的原因
     */
    private static String reason(Throwable error) {
        Throwable cursor = error;
        String message = null;
        while (cursor != null) {
            if (cursor.getMessage() != null && !cursor.getMessage().isBlank()) {
                message = cursor.getMessage();
            }
            Throwable next = cursor.getCause();
            cursor = next == cursor ? null : next;
        }
        String cleaned = SdkLog.text(message == null || message.isBlank() ? "平台查询失败" : message);
        return cleaned.length() <= 512 ? cleaned : cleaned.substring(0, 512);
    }
/**
 * 已入账的订单只更新快照。未入账且已完成的才记返佣。
 */

    private void upsert(String platform, IncomingOrderDto incoming) {
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
        // 平台商品号长于 26。本系统商品 id 只在归因到快照后才有，同步不能把它盖掉。
        order.setPlatformGoodsId(blankToNull(incoming.goodsId()));
        order.setGoodsTitle(incoming.goodsTitle() == null || incoming.goodsTitle().isBlank() ? "商品" : incoming.goodsTitle());
        order.setGoodsImageUrl(incoming.goodsImageUrl());
        order.setPayAmount(incoming.payAmount());
        order.setMediaCommission(incoming.mediaCommission());
        order.setOrderStatus(incoming.orderStatus() == null ? OrderStatus.UNKNOWN : incoming.orderStatus());
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

    /**
     * 空白字符串当成没有值。
     *
     * @param value 原始字符串
     * @return 去掉空白后的值；空白则为空
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
