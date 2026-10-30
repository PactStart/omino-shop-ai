package com.omino.shopai.app.service.order;

import com.omino.shopai.app.dto.OrderDetailDto;

import com.omino.shopai.app.dto.PageDto;

import com.omino.shopai.app.dto.OrderViewDto;

import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.AppOrder;
import com.omino.shopai.enums.OrderStatus;
import com.omino.shopai.enums.Platform;
import com.omino.shopai.enums.SettleStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * OrderQueryService 编排用例，事务和校验放在这里。
 */

@Service
public class OrderQueryService {

    private final IOrder store;

    public OrderQueryService(IOrder store) {
        this.store = store;
    }
/**
 * 按条件返回列表。
 */

    public PageDto<OrderViewDto> list(String userId, String platform, String orderStatus, String settleStatus, int page, int size) {
        String platformFilter = platformFilter(platform);
        String statusFilter = orderStatusFilter(orderStatus);
        String settleFilter = settleFilter(settleStatus);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        List<OrderViewDto> rows = store.list(userId, platformFilter, statusFilter, settleFilter, safePage, safeSize)
                .stream()
                .map(OrderQueryService::view)
                .toList();
        return new PageDto<OrderViewDto>(rows, safePage, safeSize, store.count(userId, platformFilter, statusFilter, settleFilter));
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

    public OrderDetailDto detail(String userId, String id) {
        AppOrder order = store.findById(id);
        if (order == null || order.getUserId() == null || !order.getUserId().equals(userId)) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "订单不存在");
        }
        return new OrderDetailDto(
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

    private static OrderViewDto view(AppOrder order) {
        return new OrderViewDto(
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

    /**
     * 空串表示不筛选。认不出的平台码拒绝。
     *
     * @param value 请求里的平台码
     * @return 库存用的平台码；不筛选则为空
     */
    private static String platformFilter(String value) {
        return code(value, "平台不正确", Platform::fromCode);
    }

    /**
     * 空串表示不筛选。认不出的订单状态拒绝。
     *
     * @param value 请求里的订单状态
     * @return 库存用的状态码；不筛选则为空
     */
    private static String orderStatusFilter(String value) {
        return code(value, "订单状态不正确", OrderStatus::fromCode);
    }

    /**
     * 空串表示不筛选。认不出的结算状态拒绝。
     *
     * @param value 请求里的结算状态
     * @return 库存用的状态码；不筛选则为空
     */
    private static String settleFilter(String value) {
        return code(value, "结算状态不正确", SettleStatus::fromCode);
    }

    /**
     * 用枚举校验请求里的码，再交回库存用的字符串。
     *
     * @param value 请求值
     * @param message 认不出时的错误说明
     * @param parser 枚举还原
     * @return 库存码；不筛选则为空
     */
    private static String code(String value, String message, java.util.function.Function<String, ?> parser) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Object parsed;
        try {
            parsed = parser.apply(value);
        } catch (IllegalArgumentException ex) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, message);
        }
        if (parsed instanceof Platform platform) {
            return platform.code();
        }
        if (parsed instanceof OrderStatus status) {
            return status.code();
        }
        if (parsed instanceof SettleStatus status) {
            return status.code();
        }
        throw new ApiBusinessException(ErrorCodes.VALIDATION, message);
    }

    private static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String time(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }
}
