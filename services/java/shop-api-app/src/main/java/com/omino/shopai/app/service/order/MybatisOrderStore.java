package com.omino.shopai.app.service.order;

import com.omino.shopai.enums.Platform;
import com.omino.shopai.enums.WalletLedgerType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.entity.AppOrder;
import com.omino.shopai.entity.OrderSyncCursor;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.WalletLedger;
import com.omino.shopai.mapper.AppOrderMapper;
import com.omino.shopai.mapper.OrderSyncCursorMapper;
import com.omino.shopai.mapper.WalletLedgerMapper;
import com.omino.shopai.mapper.WalletMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
/**
 * 持久化边界。事务和状态判断由调用方负责。
 */

@Repository
public class MybatisOrderStore implements OrderStore {

    private final AppOrderMapper orders;
    private final OrderSyncCursorMapper cursors;
    private final WalletMapper wallets;
    private final WalletLedgerMapper ledgers;
    private final JdbcTemplate jdbc;
/**
 * 订单、游标和钱包的 MyBatis 访问。状态判断不放这里。
 */

    public MybatisOrderStore(
            AppOrderMapper orders,
            OrderSyncCursorMapper cursors,
            WalletMapper wallets,
            WalletLedgerMapper ledgers,
            JdbcTemplate jdbc) {
        this.orders = orders;
        this.cursors = cursors;
        this.wallets = wallets;
        this.ledgers = ledgers;
        this.jdbc = jdbc;
    }
/**
 * 取出平台同步游标。没有则建一条，供本次从该点拉取。
 */

    @Override
    public OrderSyncCursor cursor(String platform) {
        OrderSyncCursor row = cursors.selectById(platform);
        if (row != null) {
            return row;
        }
        OrderSyncCursor blank = new OrderSyncCursor();
        blank.setPlatform(Platform.fromCode(platform));
        return blank;
    }

    @Override
    public void advance(String platform, OffsetDateTime now) {
        jdbc.update("""
                INSERT INTO order_sync_cursor
                    (platform, last_sync_create_time, last_sync_update_time, last_error, updated_at)
                VALUES (?, ?, ?, NULL, ?)
                ON CONFLICT (platform) DO UPDATE SET
                    last_sync_create_time = EXCLUDED.last_sync_create_time,
                    last_sync_update_time = EXCLUDED.last_sync_update_time,
                    last_error = NULL,
                    updated_at = EXCLUDED.updated_at
                """, platform, now, now, now);
    }

    @Override
    public AppOrder findByPlatformOrder(String platform, String platformOrderId) {
        return orders.selectOne(new LambdaQueryWrapper<AppOrder>()
                .eq(AppOrder::getPlatform, platform)
                .eq(AppOrder::getPlatformOrderId, platformOrderId));
    }
/**
 * 保存修改。
 */

    @Override
    public void save(AppOrder order) {
        if (orders.selectById(order.getId()) == null) {
            orders.insert(order);
        } else {
            orders.updateById(order);
        }
    }
/**
 * 按用户锁钱包行，避免并发入账把余额写丢。
 */

    @Override
    public Wallet lockWallet(String userId) {
        jdbc.queryForList("SELECT user_id FROM wallet WHERE user_id = ? FOR UPDATE", String.class, userId);
        return wallets.selectById(userId);
    }
/**
 * 保存修改。
 */

    @Override
    public void saveWallet(Wallet wallet) {
        if (wallets.selectById(wallet.getUserId()) == null) {
            wallets.insert(wallet);
        } else {
            wallets.updateById(wallet);
        }
    }

    @Override
    public WalletLedger findCommissionByOrder(String orderId) {
        return ledgers.selectOne(new LambdaQueryWrapper<WalletLedger>()
                .eq(WalletLedger::getType, WalletLedgerType.COMMISSION)
                .eq(WalletLedger::getOrderId, orderId)
                .last("LIMIT 1"));
    }

    @Override
    public void insertLedger(WalletLedger ledger) {
        ledgers.insert(ledger);
    }
/**
 * 数出该订单已有的返佣流水，防止同步再记一笔。
 */

    @Override
    public int commissionCount(String userId) {
        Long count = ledgers.selectCount(new LambdaQueryWrapper<WalletLedger>()
                .eq(WalletLedger::getUserId, userId)
                .eq(WalletLedger::getType, WalletLedgerType.COMMISSION));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public List<AppOrder> list(String userId, String platform, String orderStatus, String settleStatus, int page, int size) {
        return orders.selectList(filter(userId, platform, orderStatus, settleStatus)
                .orderByDesc(AppOrder::getCreatedAt)
                .last("LIMIT " + size + " OFFSET " + ((long) (page - 1) * size)));
    }
/**
 * 按筛选条件数订单，供分页总数。
 */

    @Override
    public long count(String userId, String platform, String orderStatus, String settleStatus) {
        Long count = orders.selectCount(filter(userId, platform, orderStatus, settleStatus));
        return count == null ? 0 : count;
    }

    @Override
    public AppOrder findById(String id) {
        return orders.selectById(id);
    }
/**
 * 拼订单列表条件。平台和状态在边界上用枚举码比较。
 */

    private static LambdaQueryWrapper<AppOrder> filter(String userId, String platform, String orderStatus, String settleStatus) {
        LambdaQueryWrapper<AppOrder> wrapper = new LambdaQueryWrapper<AppOrder>().eq(AppOrder::getUserId, userId);
        if (platform != null) {
            wrapper.eq(AppOrder::getPlatform, platform);
        }
        if (orderStatus != null) {
            wrapper.eq(AppOrder::getOrderStatus, orderStatus);
        }
        if (settleStatus != null) {
            wrapper.eq(AppOrder::getSettleStatus, settleStatus);
        }
        return wrapper;
    }
}
