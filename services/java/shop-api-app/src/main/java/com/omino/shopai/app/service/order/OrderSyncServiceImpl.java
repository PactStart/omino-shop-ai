package com.omino.shopai.app.service.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.app.adapter.platform.IPlatformGoods;
import com.omino.shopai.exception.PlatformCallException;
import com.omino.shopai.app.util.SdkLog;
import com.omino.shopai.app.util.UnionMaps;
import com.omino.shopai.dto.order.IncomingOrderDto;
import com.omino.shopai.entity.AppOrder;
import com.omino.shopai.entity.AppUser;
import com.omino.shopai.entity.OrderSyncCursor;
import com.omino.shopai.entity.PlatformAuthorization;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.WalletLedger;
import com.omino.shopai.enums.OrderStatus;
import com.omino.shopai.enums.Platform;
import com.omino.shopai.enums.SettleStatus;
import com.omino.shopai.enums.WalletLedgerType;
import com.omino.shopai.mapper.AppOrderMapper;
import com.omino.shopai.mapper.AppUserMapper;
import com.omino.shopai.mapper.OrderSyncCursorMapper;
import com.omino.shopai.mapper.PlatformAuthorizationMapper;
import com.omino.shopai.mapper.WalletLedgerMapper;
import com.omino.shopai.mapper.WalletMapper;
import com.omino.shopai.support.Ids;
import com.omino.shopai.support.withdrawal.WalletMath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * 按平台允许的跨度把订单追到本次请求时间，并在同一事务里入账。
 */
@Service
public class OrderSyncServiceImpl implements IOrderSyncService {

    private static final ObjectMapper EXTRAS = new ObjectMapper();

    private final AppOrderMapper orders;
    private final OrderSyncCursorMapper cursors;
    private final WalletMapper wallets;
    private final WalletLedgerMapper ledgers;
    private final JdbcTemplate jdbc;
    private final List<IPlatformGoods> clients;
    private final PlatformAuthorizationMapper authorizations;
    private final AppUserMapper users;
    private final TransactionOperations tx;
    private final Clock clock;
    private final Executor executor;
    private final BigDecimal rate;
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    /**
     * 测试用。追赶在调用线程里做完。
     *
     * @param orders 订单表
     * @param cursors 同步游标表
     * @param wallets 钱包表
     * @param ledgers 钱包流水表
     * @param jdbc 游标写入和钱包行锁
     * @param clients 各平台适配器
     * @param tx 事务
     * @param clock 本次请求时间
     * @param shareRate 用户分成比例
     */
    public OrderSyncServiceImpl(
            AppOrderMapper orders,
            OrderSyncCursorMapper cursors,
            WalletMapper wallets,
            WalletLedgerMapper ledgers,
            JdbcTemplate jdbc,
            List<IPlatformGoods> clients,
            TransactionOperations tx,
            Clock clock,
            String shareRate) {
        this(orders, cursors, wallets, ledgers, jdbc, clients, null, null, tx, clock, shareRate, Runnable::run);
    }

    /**
     * 注入订单来源、事务、分成比例和追赶线程。比例必须在 0 到 1 之间。
     *
     * @param orders 订单表
     * @param cursors 同步游标表
     * @param wallets 钱包表
     * @param ledgers 钱包流水表
     * @param jdbc 游标写入和钱包行锁
     * @param clients 各平台适配器
     * @param authorizations 联盟授权表，用来把推广身份换成用户
     * @param users 用户表，首次归因时读取邀请人
     * @param tx 事务
     * @param clock 本次请求时间
     * @param shareRate 用户分成比例
     * @param orderSyncExecutor 异步追赶用的线程
     */
    @Autowired
    public OrderSyncServiceImpl(
            AppOrderMapper orders,
            OrderSyncCursorMapper cursors,
            WalletMapper wallets,
            WalletLedgerMapper ledgers,
            JdbcTemplate jdbc,
            List<IPlatformGoods> clients,
            PlatformAuthorizationMapper authorizations,
            AppUserMapper users,
            TransactionOperations tx,
            Clock clock,
            @Value("${commission.user-share-rate:0.80}") String shareRate,
            @Qualifier("orderSyncExecutor") Executor orderSyncExecutor) {
        this.orders = orders;
        this.cursors = cursors;
        this.wallets = wallets;
        this.ledgers = ledgers;
        this.jdbc = jdbc;
        this.clients = clients;
        this.authorizations = authorizations;
        this.users = users;
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
     * @return 无
     */
    @Override
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
     * @return 无
     */
    @Override
    public void sync(String platform) {
        OffsetDateTime requestedAt = OffsetDateTime.now(clock);
        Duration span = UnionMaps.maxSpan(platform);
        while (true) {
            OrderSyncCursor stored = cursor(platform);
            UnionMaps.Window created = UnionMaps.slice(stored.getLastSyncCreateTime(), requestedAt, span);
            UnionMaps.Window updated = UnionMaps.slice(stored.getLastSyncUpdateTime(), requestedAt, span);
            if (created == null && updated == null) {
                return;
            }
            List<IncomingOrderDto> batch = pull(platform, stored, created, updated);
            // 本段写库和水位前进同一事务。中途失败则这段水位不动，下次从这里重拉。
            tx.executeWithoutResult(status -> {
                for (IncomingOrderDto incoming : batch) {
                    upsert(platform, incoming);
                }
                advance(
                        platform,
                        created == null ? null : created.end(),
                        updated == null ? null : updated.end());
            });
        }
    }

    /**
     * 找到这个平台的适配器，只拉服务给出的两段。没有对应适配器则按上游失败返回。
     *
     * @param platform 平台码
     * @param cursor 当前游标
     * @param created 下单时间这一段
     * @param updated 更新时间这一段
     * @return 这两段里的订单
     */
    protected List<IncomingOrderDto> pull(
            String platform, OrderSyncCursor cursor, UnionMaps.Window created, UnionMaps.Window updated) {
        Platform parsed;
        try {
            parsed = Platform.fromCode(platform);
        } catch (IllegalArgumentException ex) {
            throw new PlatformCallException();
        }
        for (IPlatformGoods client : clients) {
            if (parsed != null && client.platform() == parsed) {
                return client.pullOrders(cursor, created, updated);
            }
        }
        throw new PlatformCallException();
    }

    /**
     * 这一平台失败时记下原因。两列水位都不改。
     *
     * @param platform 平台码
     * @param error 这次失败
     * @return 无
     */
    @Override
    public void noteFailure(String platform, Throwable error) {
        fail(platform, reason(error));
    }

    /**
     * 取出平台同步游标。没有则建一条，供本次从该点拉取。
     *
     * @param platform 平台码
     * @return 已有游标；没有则是空水位
     */
    protected OrderSyncCursor cursor(String platform) {
        OrderSyncCursor row = cursors.selectById(platform);
        if (row != null) {
            return row;
        }
        OrderSyncCursor blank = new OrderSyncCursor();
        blank.setPlatform(Platform.fromCode(platform));
        return blank;
    }

    /**
     * 只推进这次拉完的列。空参数保留库里的原水位，成功则清掉失败文案。
     *
     * @param platform 平台码
     * @param createTo 下单水位的新值；空表示这一列本次没拉
     * @param updateTo 更新水位的新值；空表示这一列本次没拉
     * @return 无
     */
    protected void advance(String platform, OffsetDateTime createTo, OffsetDateTime updateTo) {
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO order_sync_cursor
                    (platform, last_sync_create_time, last_sync_update_time, last_error, updated_at)
                VALUES (?, ?, ?, NULL, ?)
                ON CONFLICT (platform) DO UPDATE SET
                    last_sync_create_time = COALESCE(EXCLUDED.last_sync_create_time, order_sync_cursor.last_sync_create_time),
                    last_sync_update_time = COALESCE(EXCLUDED.last_sync_update_time, order_sync_cursor.last_sync_update_time),
                    last_error = NULL,
                    updated_at = EXCLUDED.updated_at
                """, platform, createTo, updateTo, now);
    }

    /**
     * 只写失败文案，不改两列水位。
     *
     * @param platform 平台码
     * @param message 失败原因
     * @return 无
     */
    protected void fail(String platform, String message) {
        jdbc.update("""
                INSERT INTO order_sync_cursor
                    (platform, last_sync_create_time, last_sync_update_time, last_error, updated_at)
                VALUES (?, NULL, NULL, ?, ?)
                ON CONFLICT (platform) DO UPDATE SET
                    last_error = EXCLUDED.last_error,
                    updated_at = EXCLUDED.updated_at
                """, platform, message, OffsetDateTime.now());
    }

    /**
     * 按平台和平台订单号读取本地订单。
     *
     * @param platform 平台码
     * @param platformOrderId 平台订单号
     * @return 本地订单；没有则空
     */
    protected AppOrder findByPlatformOrder(String platform, String platformOrderId) {
        return orders.selectOne(new LambdaQueryWrapper<AppOrder>()
                .eq(AppOrder::getPlatform, platform)
                .eq(AppOrder::getPlatformOrderId, platformOrderId));
    }

    /**
     * 没有订单行就插入，已有则按主键更新。
     *
     * @param order 本地订单
     * @return 无
     */
    protected void saveOrder(AppOrder order) {
        if (orders.selectById(order.getId()) == null) {
            orders.insert(order);
        } else {
            orders.updateById(order);
        }
    }

    /**
     * 按用户锁钱包行，避免并发入账把余额写丢。
     *
     * @param userId 用户主键
     * @return 钱包；没有则空
     */
    protected Wallet lockWallet(String userId) {
        jdbc.queryForList("SELECT user_id FROM wallet WHERE user_id = ? FOR UPDATE", String.class, userId);
        return wallets.selectById(userId);
    }

    /**
     * 没有钱包行就插入，已有则更新。
     *
     * @param wallet 钱包
     * @return 无
     */
    protected void saveWallet(Wallet wallet) {
        if (wallets.selectById(wallet.getUserId()) == null) {
            wallets.insert(wallet);
        } else {
            wallets.updateById(wallet);
        }
    }

    /**
     * 找出该订单已有的返佣流水，防止同步再记一笔。
     *
     * @param orderId 订单主键
     * @return 返佣流水；没有则空
     */
    protected WalletLedger findCommissionByOrder(String orderId) {
        return ledgers.selectOne(new LambdaQueryWrapper<WalletLedger>()
                .eq(WalletLedger::getType, WalletLedgerType.COMMISSION)
                .eq(WalletLedger::getOrderId, orderId)
                .last("LIMIT 1"));
    }

    /**
     * 插入一条钱包流水。
     *
     * @param ledger 流水
     * @return 无
     */
    protected void insertLedger(WalletLedger ledger) {
        ledgers.insert(ledger);
    }

    /**
     * 补充字段只接受 JSON 对象。空白当成没有，避免把空串写进检查约束。
     *
     * @param raw 适配器交来的 JSON
     * @return 紧凑的对象字符串；没有则为空
     */
    private static String extras(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        JsonNode node;
        try {
            node = EXTRAS.readTree(raw);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("extras 不是 JSON 对象");
        }
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("extras 不是 JSON 对象");
        }
        return node.toString();
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
     *
     * @param platform 平台码
     * @param incoming 这一笔平台订单
     * @return 无
     */
    private void upsert(String platform, IncomingOrderDto incoming) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        AppOrder existing = findByPlatformOrder(platform, incoming.platformOrderId());
        AppOrder order = existing == null ? newOrder(platform, incoming.platformOrderId(), now) : existing;
        boolean booked = existing != null && (existing.getWalletLedgerId() != null
                || findCommissionByOrder(existing.getId()) != null);
        BigDecimal bookedCommission = order.getUserCommission();
        String bookedLedger = order.getWalletLedgerId();
        OffsetDateTime bookedSettledAt = order.getSettledAt();

        String userId = blankToNull(incoming.userId());
        if (userId == null) {
            userId = blankToNull(userIdByRelation(platform, incoming.relationId()));
        }
        // 这次没对上用户时保留已有归因，避免一次空回传把订单从用户名下拿掉。
        if (userId != null) {
            order.setUserId(userId);
        }
        String relationId = blankToNull(incoming.relationId());
        if (relationId != null) {
            order.setRelationId(relationId);
        }
        // 上级只在第一次归因时写入。之后平台再同步也不改。
        if (order.getParentUserId() == null) {
            String parent = blankToNull(incoming.parentUserId());
            if (parent == null && order.getUserId() != null) {
                parent = blankToNull(inviterId(order.getUserId()));
            }
            order.setParentUserId(parent);
        }
        // 平台商品号长于 26。本系统商品 id 只在归因到快照后才有，同步不能把它盖掉。
        order.setPlatformGoodsId(blankToNull(incoming.goodsId()));
        order.setGoodsTitle(incoming.goodsTitle() == null || incoming.goodsTitle().isBlank() ? "商品" : incoming.goodsTitle());
        order.setGoodsImageUrl(incoming.goodsImageUrl());
        order.setPayAmount(incoming.payAmount());
        order.setMediaCommission(incoming.mediaCommission());
        order.setOrderStatus(incoming.orderStatus() == null ? OrderStatus.UNKNOWN : incoming.orderStatus());
        order.setOrderedAt(incoming.orderedAt());
        order.setPaidAt(incoming.paidAt());
        order.setShippedAt(incoming.shippedAt());
        order.setCompletedAt(incoming.completedAt());
        order.setExtras(extras(incoming.extras()));
        order.setUpdatedFromPlatformAt(now);

        // 已经记过返佣的订单只更新商品快照，佣金和流水号保持第一次的值。
        if (booked) {
            order.setUserCommission(bookedCommission);
            order.setSettleStatus(SettleStatus.SETTLED);
            order.setWalletLedgerId(bookedLedger);
            order.setSettledAt(bookedSettledAt);
            saveOrder(order);
            return;
        }

        BigDecimal userCommission = incoming.mediaCommission() == null
                ? null
                : incoming.mediaCommission().multiply(rate).setScale(2, RoundingMode.HALF_UP);
        order.setUserCommission(userCommission);
        order.setSettleStatus(settleStatus(order));
        order.setWalletLedgerId(null);
        saveOrder(order);
        if (OrderStatus.COMPLETED == order.getOrderStatus()
                && SettleStatus.PENDING == order.getSettleStatus()
                && userCommission != null
                && userCommission.signum() > 0
                && findCommissionByOrder(order.getId()) == null) {
            credit(order, userCommission, now);
        }
    }

    /**
     * 锁钱包后入账，并把流水号写回订单，防止下次再记。
     *
     * @param order 本地订单
     * @param amount 用户佣金
     * @param now 入账时间
     * @return 无
     */
    private void credit(AppOrder order, BigDecimal amount, OffsetDateTime now) {
        // 锁住买家钱包再入账，并把流水号写回订单，下次同步看到就不再记一笔。
        Wallet wallet = lockWallet(order.getUserId());
        if (wallet == null) {
            wallet = new Wallet();
            wallet.setUserId(order.getUserId());
            wallet.setAvailableAmount(BigDecimal.ZERO.setScale(2));
            wallet.setFrozenAmount(BigDecimal.ZERO.setScale(2));
            wallet.setTotalEarned(BigDecimal.ZERO.setScale(2));
            wallet.setUpdatedAt(now);
        }
        WalletMath.credit(wallet, amount, now);
        saveWallet(wallet);
        WalletLedger ledger = new WalletLedger();
        ledger.setId(Ids.ulid());
        ledger.setUserId(order.getUserId());
        ledger.setType(WalletLedgerType.COMMISSION);
        ledger.setAmount(amount);
        ledger.setOrderId(order.getId());
        ledger.setCreatedAt(now);
        insertLedger(ledger);
        order.setWalletLedgerId(ledger.getId());
        order.setSettleStatus(SettleStatus.SETTLED);
        order.setSettledAt(now);
        saveOrder(order);
    }

    /**
     * 按平台和平台订单号建本地行。平台写成枚举。
     *
     * @param platform 平台码
     * @param platformOrderId 平台订单号
     * @param now 创建时间
     * @return 尚未写入的订单
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
     * 按订单上已有的流水、买家和状态决定结算状态。
     *
     * @param order 本地订单
     * @return 结算状态
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
     * 用平台带回的推广身份找下单用户。同一身份有多行时先取本平台，没有再取任意一行。
     * 拼多多订单上的 uid 记在京东授权行上。
     *
     * @param platform 本段订单的平台码
     * @param relationId 平台带回的推广身份
     * @return 用户号；没有记录则为空
     */
    protected String userIdByRelation(String platform, String relationId) {
        if (authorizations == null || relationId == null || relationId.isBlank()) {
            return null;
        }
        List<PlatformAuthorization> rows = authorizations.selectList(new LambdaQueryWrapper<PlatformAuthorization>()
                .eq(PlatformAuthorization::getRelationId, relationId));
        Platform current = Platform.fromCode(platform);
        String fallback = null;
        for (PlatformAuthorization row : rows) {
            String userId = blankToNull(row.getUserId());
            if (userId == null) {
                continue;
            }
            if (row.getPlatform() == current) {
                return userId;
            }
            if (fallback == null) {
                fallback = userId;
            }
        }
        return fallback;
    }

    /**
     * 读取这个用户的邀请人，作为订单的上级。
     *
     * @param userId 下单用户
     * @return 邀请人；没有则为空
     */
    protected String inviterId(String userId) {
        if (users == null || userId == null || userId.isBlank()) {
            return null;
        }
        AppUser user = users.selectById(userId);
        return user == null ? null : user.getInviterId();
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
