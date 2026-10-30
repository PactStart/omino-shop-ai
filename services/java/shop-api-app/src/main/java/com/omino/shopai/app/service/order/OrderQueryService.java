package com.omino.shopai.app.service.order;

import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.AppOrder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * OrderQueryService 编排用例，事务和校验放在这里。
 */

@Service
public class OrderQueryService {

    private static final Set<String> PLATFORMS = Set.of("taobao", "jd", "pdd", "vip");
    private static final Set<String> ORDER_STATUSES = Set.of(
            "pending_pay", "pending_ship", "shipped", "completed", "cancelled", "unknown");
    private static final Set<String> SETTLE_STATUSES = Set.of("none", "pending", "settled");

    private final OrderStore store;

    public OrderQueryService(OrderStore store) {
        this.store = store;
    }
/**
 * 按条件返回列表。
 */

    public OrderPage list(String userId, String platform, String orderStatus, String settleStatus, int page, int size) {
        String platformFilter = filter(platform, PLATFORMS, "平台不正确");
        String statusFilter = filter(orderStatus, ORDER_STATUSES, "订单状态不正确");
        String settleFilter = filter(settleStatus, SETTLE_STATUSES, "结算状态不正确");
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        List<OrderView> rows = store.list(userId, platformFilter, statusFilter, settleFilter, safePage, safeSize)
                .stream()
                .map(OrderQueryService::view)
                .toList();
        return new OrderPage(rows, safePage, safeSize, store.count(userId, platformFilter, statusFilter, settleFilter));
    }

    public Map<String, Object> stats(String userId, OffsetDateTime from, OffsetDateTime to) {
        List<AppOrder> rows = store.list(userId, null, null, null, 1, 500);
        BigDecimal pay = BigDecimal.ZERO;
        int count = 0;
        for (AppOrder row : rows) {
            if (row.getOrderedAt() != null && from != null && row.getOrderedAt().isBefore(from)) {
                continue;
            }
            if (row.getOrderedAt() != null && to != null && row.getOrderedAt().isAfter(to)) {
                continue;
            }
            count++;
            if (row.getPayAmount() != null) {
                pay = pay.add(row.getPayAmount());
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count", count);
        body.put("payAmount", money(pay));
        return body;
    }
/**
 * 读取详情。
 */

    public OrderDetail detail(String userId, String id) {
        AppOrder order = store.findById(id);
        if (order == null || order.getUserId() == null || !order.getUserId().equals(userId)) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "订单不存在");
        }
        return new OrderDetail(
                order.getId(),
                order.getPlatform() == null ? null : order.getPlatform().code(),
                order.getGoodsId(),
                order.getGoodsTitle(),
                order.getGoodsImageUrl(),
                money(order.getPayAmount()),
                money(order.getUserCommission()),
                order.getOrderStatus() == null ? null : order.getOrderStatus().code(),
                order.getSettleStatus() == null ? null : order.getSettleStatus().code(),
                time(order.getOrderedAt()),
                time(order.getShippedAt()),
                null);
    }

    private static OrderView view(AppOrder order) {
        return new OrderView(
                order.getId(),
                order.getPlatform() == null ? null : order.getPlatform().code(),
                order.getGoodsId(),
                order.getGoodsTitle(),
                order.getGoodsImageUrl(),
                money(order.getPayAmount()),
                money(order.getUserCommission()),
                order.getOrderStatus() == null ? null : order.getOrderStatus().code(),
                order.getSettleStatus() == null ? null : order.getSettleStatus().code(),
                time(order.getOrderedAt()),
                time(order.getShippedAt()));
    }
/**
 * 按平台和状态筛订单。对外仍用枚举码字符串。
 */

    private static String filter(String value, Set<String> allowed, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!allowed.contains(value)) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, message);
        }
        return value;
    }

    private static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String time(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }
}
