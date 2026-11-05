package com.omino.shopai.app.order;

import com.omino.shopai.app.adapter.platform.IPlatformGoods;
import com.omino.shopai.app.service.order.OrderSyncServiceImpl;
import com.omino.shopai.app.util.UnionMaps;
import com.omino.shopai.dto.order.IncomingOrderDto;
import com.omino.shopai.entity.AppOrder;
import com.omino.shopai.entity.OrderSyncCursor;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.WalletLedger;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 用内存订单替换同步里的表访问，追赶和入账仍走真实实现。
 */
final class MemoryOrderSync extends OrderSyncServiceImpl {

    private final MemoryOrders orders;
    private final Pull pull;

    /**
     * 绑定同一份内存订单，按给出的适配器拉单。追赶在调用线程里做完。
     *
     * @param orders 内存订单和事务
     * @param clients 各平台适配器
     * @param clock 本次请求时间
     * @param shareRate 用户分成比例
     */
    MemoryOrderSync(MemoryOrders orders, List<IPlatformGoods> clients, Clock clock, String shareRate) {
        this(orders, clients, null, clock, shareRate);
    }

    /**
     * 绑定同一份内存订单。给出拉单桩时不再查找适配器。
     *
     * @param orders 内存订单和事务
     * @param pull 替换拉单；空则走适配器
     * @param clock 本次请求时间
     * @param shareRate 用户分成比例
     */
    MemoryOrderSync(MemoryOrders orders, Pull pull, Clock clock, String shareRate) {
        this(orders, List.of(), pull, clock, shareRate);
    }

    /**
     * 绑定同一份内存订单。
     *
     * @param orders 内存订单和事务
     * @param clients 各平台适配器
     * @param pull 替换拉单；空则走适配器
     * @param clock 本次请求时间
     * @param shareRate 用户分成比例
     */
    private MemoryOrderSync(MemoryOrders orders, List<IPlatformGoods> clients, Pull pull, Clock clock, String shareRate) {
        super(null, null, null, null, null, clients, orders, clock, shareRate);
        this.orders = orders;
        this.pull = pull;
    }

    /**
     * 测试桩给出这一段订单，否则按平台适配器拉。
     *
     * @param platform 平台码
     * @param cursor 当前游标
     * @param created 下单时间这一段
     * @param updated 更新时间这一段
     * @return 这两段里的订单
     */
    @Override
    protected List<IncomingOrderDto> pull(
            String platform, OrderSyncCursor cursor, UnionMaps.Window created, UnionMaps.Window updated) {
        if (pull != null) {
            return pull.pull(platform, cursor, created, updated);
        }
        return super.pull(platform, cursor, created, updated);
    }

    /**
     * 从内存订单读取游标。没有则是空水位。
     *
     * @param platform 平台码
     * @return 游标
     */
    @Override
    protected OrderSyncCursor cursor(String platform) {
        return orders.cursor(platform);
    }

    /**
     * 把这次拉完的水位写进内存订单。
     *
     * @param platform 平台码
     * @param createTo 下单水位的新值；空表示这一列本次没拉
     * @param updateTo 更新水位的新值；空表示这一列本次没拉
     * @return 无
     */
    @Override
    protected void advance(String platform, OffsetDateTime createTo, OffsetDateTime updateTo) {
        orders.advance(platform, createTo, updateTo);
    }

    /**
     * 把失败文案写进内存游标，不改两列水位。
     *
     * @param platform 平台码
     * @param message 失败原因
     * @return 无
     */
    @Override
    protected void fail(String platform, String message) {
        orders.fail(platform, message);
    }

    /**
     * 从内存订单按平台订单号读取。
     *
     * @param platform 平台码
     * @param platformOrderId 平台订单号
     * @return 本地订单；没有则空
     */
    @Override
    protected AppOrder findByPlatformOrder(String platform, String platformOrderId) {
        return orders.findByPlatformOrder(platform, platformOrderId);
    }

    /**
     * 把订单写回内存订单。
     *
     * @param order 本地订单
     * @return 无
     */
    @Override
    protected void saveOrder(AppOrder order) {
        orders.save(order);
    }

    /**
     * 从内存订单读取钱包。
     *
     * @param userId 用户主键
     * @return 钱包；没有则空
     */
    @Override
    protected Wallet lockWallet(String userId) {
        return orders.lockWallet(userId);
    }

    /**
     * 把钱包写回内存订单。
     *
     * @param wallet 钱包
     * @return 无
     */
    @Override
    protected void saveWallet(Wallet wallet) {
        orders.saveWallet(wallet);
    }

    /**
     * 从内存流水找出该订单的返佣。
     *
     * @param orderId 订单主键
     * @return 返佣流水；没有则空
     */
    @Override
    protected WalletLedger findCommissionByOrder(String orderId) {
        return orders.findCommissionByOrder(orderId);
    }

    /**
     * 把流水写进内存订单。
     *
     * @param ledger 流水
     * @return 无
     */
    @Override
    protected void insertLedger(WalletLedger ledger) {
        orders.insertLedger(ledger);
    }

    /**
     * 测试里替换向平台拉这一段订单。
     */
    @FunctionalInterface
    interface Pull {

        /**
         * 给出这一段订单。
         *
         * @param platform 平台码
         * @param cursor 当前游标
         * @param created 下单时间这一段
         * @param updated 更新时间这一段
         * @return 这两段里的订单
         */
        List<IncomingOrderDto> pull(
                String platform, OrderSyncCursor cursor, UnionMaps.Window created, UnionMaps.Window updated);
    }
}
