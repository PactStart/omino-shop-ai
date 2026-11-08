package com.omino.shopai.app.service.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.dto.OrderDetailDto;
import com.omino.shopai.app.dto.OrderViewDto;
import com.omino.shopai.dto.PageDto;
import com.omino.shopai.entity.AppOrder;
import com.omino.shopai.enums.OrderStatus;
import com.omino.shopai.enums.Platform;
import com.omino.shopai.enums.SettleStatus;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.AppOrderMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 按用户列出电商订单。平台和状态在读表之前用枚举校验。
 */
@Service
public class OrderQueryServiceImpl implements IOrderQueryService {

    private final AppOrderMapper orders;

    /**
     * 注入订单表。筛选和分页在读之前做完。
     *
     * @param orders 订单表
     */
    public OrderQueryServiceImpl(AppOrderMapper orders) {
        this.orders = orders;
    }

    /**
     * 按条件返回列表。页码和页大小先收进允许范围。
     *
     * @param userId 用户主键
     * @param platform 平台码，空表示不筛选
     * @param orderStatus 订单状态，空表示不筛选
     * @param settleStatus 结算状态，空表示不筛选
     * @param keyword 标题或平台订单号，空表示不搜
     * @param page 页码，从 1 起
     * @param size 页大小，最大 50
     * @return 订单页
     */
    @Override
    public PageDto<OrderViewDto> list(String userId, String platform, String orderStatus, String settleStatus, String keyword, int page, int size) {
        String platformFilter = platformFilter(platform);
        String statusFilter = orderStatusFilter(orderStatus);
        String settleFilter = settleFilter(settleStatus);
        String keywordFilter = keywordFilter(keyword);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        List<OrderViewDto> rows = listOrders(userId, platformFilter, statusFilter, settleFilter, keywordFilter, safePage, safeSize)
                .stream()
                .map(OrderQueryServiceImpl::view)
                .toList();
        return new PageDto<OrderViewDto>(rows, safePage, safeSize, countOrders(userId, platformFilter, statusFilter, settleFilter, keywordFilter));
    }

    /**
     * 汇总该用户一段时间的订单笔数和支付金额。
     *
     * @param userId 用户主键
     * @param from 开始时间，可空
     * @param to 结束时间，可空
     * @return 笔数 count 和支付金额 payAmount
     */
    @Override
    public Map<String, Object> stats(String userId, OffsetDateTime from, OffsetDateTime to) {
        List<AppOrder> rows = listOrders(userId, null, null, null, null, 1, 500);
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
     * 读取详情。不属于该用户时按不存在处理。
     *
     * @param userId 用户主键
     * @param id 订单主键
     * @return 订单详情
     */
    @Override
    public OrderDetailDto detail(String userId, String id) {
        AppOrder order = findOrder(id);
        if (order == null || order.getUserId() == null || !order.getUserId().equals(userId)) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "订单不存在");
        }
        return new OrderDetailDto(
                order.getId(),
                order.getPlatform() == null ? null : order.getPlatform().code(),
                order.getPlatformOrderId(),
                order.getRelationId(),
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

    /**
     * 按筛选条件取出一页订单，创建时间新的在前。
     *
     * @param userId 用户主键
     * @param platform 平台码，空表示不筛选
     * @param orderStatus 订单状态，空表示不筛选
     * @param settleStatus 结算状态，空表示不筛选
     * @param keyword 标题或平台订单号，空表示不搜
     * @param page 页码，从 1 起
     * @param size 页大小
     * @return 当前页
     */
    protected List<AppOrder> listOrders(String userId, String platform, String orderStatus, String settleStatus, String keyword, int page, int size) {
        return orders.selectList(filter(userId, platform, orderStatus, settleStatus, keyword)
                .orderByDesc(AppOrder::getCreatedAt)
                .last("LIMIT " + size + " OFFSET " + ((long) (page - 1) * size)));
    }

    /**
     * 按筛选条件数订单，供分页总数。
     *
     * @param userId 用户主键
     * @param platform 平台码，空表示不筛选
     * @param orderStatus 订单状态，空表示不筛选
     * @param settleStatus 结算状态，空表示不筛选
     * @param keyword 标题或平台订单号，空表示不搜
     * @return 行数
     */
    protected long countOrders(String userId, String platform, String orderStatus, String settleStatus, String keyword) {
        Long count = orders.selectCount(filter(userId, platform, orderStatus, settleStatus, keyword));
        return count == null ? 0 : count;
    }

    /**
     * 按主键读取订单。
     *
     * @param id 订单主键
     * @return 订单；没有则空
     */
    protected AppOrder findOrder(String id) {
        return orders.selectById(id);
    }

    /**
     * 把订单收成列表字段。金额和时间都写成字符串。
     *
     * @param order 订单
     * @return 列表项
     */
    private static OrderViewDto view(AppOrder order) {
        return new OrderViewDto(
                order.getId(),
                order.getPlatform() == null ? null : order.getPlatform().code(),
                order.getPlatformOrderId(),
                order.getRelationId(),
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
    private static String code(String value, String message, Function<String, ?> parser) {
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

    /**
     * 空串表示不搜索。过长的词拒绝，避免把整段文本送进模糊查询。
     *
     * @param value 请求里的搜索词
     * @return 去掉首尾空白的词；不搜索则为空
     */
    private static String keywordFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String word = value.trim();
        if (word.length() > 64) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "搜索词过长");
        }
        return word;
    }

    /**
     * 金额写成两位小数字符串。没有金额时保持空。
     *
     * @param value 金额，可空
     * @return 两位小数；空输入返回空
     */
    private static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 时间写成字符串。没有时间时保持空。
     *
     * @param value 时间，可空
     * @return 时间文本；空输入返回空
     */
    private static String time(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    /**
     * 拼订单列表条件。平台和状态在边界上用枚举码比较。
     *
     * @param userId 用户主键
     * @param platform 平台码，空表示不筛选
     * @param orderStatus 订单状态，空表示不筛选
     * @param settleStatus 结算状态，空表示不筛选
     * @param keyword 标题或平台订单号，空表示不搜
     * @return 查询条件
     */
    private static LambdaQueryWrapper<AppOrder> filter(String userId, String platform, String orderStatus, String settleStatus, String keyword) {
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
        if (keyword != null) {
            // 包在 and 里，避免 or 把用户条件拆掉。
            wrapper.and(nested -> nested.like(AppOrder::getGoodsTitle, keyword).or().like(AppOrder::getPlatformOrderId, keyword));
        }
        return wrapper;
    }
}
