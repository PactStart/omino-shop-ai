package com.omino.shopai.app.order;

import com.omino.shopai.app.service.order.OrderQueryServiceImpl;
import com.omino.shopai.entity.AppOrder;

import java.util.List;

/**
 * 用内存订单替换查询里的表访问，列表和详情仍走真实实现。
 */
final class MemoryOrderQuery extends OrderQueryServiceImpl {

    private final MemoryOrders orders;

    /**
     * 绑定同一份内存订单。表访问全部转给它。
     *
     * @param orders 内存订单
     */
    MemoryOrderQuery(MemoryOrders orders) {
        super(null);
        this.orders = orders;
    }

    /**
     * 从内存订单取出一页。
     *
     * @param userId 用户主键
     * @param platform 平台码，空表示不筛选
     * @param orderStatus 订单状态，空表示不筛选
     * @param settleStatus 结算状态，空表示不筛选
     * @param page 页码，从 1 起
     * @param size 页大小
     * @return 当前页
     */
    @Override
    protected List<AppOrder> listOrders(String userId, String platform, String orderStatus, String settleStatus, int page, int size) {
        return orders.list(userId, platform, orderStatus, settleStatus, page, size);
    }

    /**
     * 数内存订单里符合条件的行。
     *
     * @param userId 用户主键
     * @param platform 平台码，空表示不筛选
     * @param orderStatus 订单状态，空表示不筛选
     * @param settleStatus 结算状态，空表示不筛选
     * @return 行数
     */
    @Override
    protected long countOrders(String userId, String platform, String orderStatus, String settleStatus) {
        return orders.count(userId, platform, orderStatus, settleStatus);
    }

    /**
     * 从内存订单按主键读取。
     *
     * @param id 订单主键
     * @return 订单；没有则空
     */
    @Override
    protected AppOrder findOrder(String id) {
        return orders.findById(id);
    }
}
