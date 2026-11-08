package com.omino.shopai.app.service.order;

import com.omino.shopai.app.dto.OrderDetailDto;
import com.omino.shopai.app.dto.OrderViewDto;
import com.omino.shopai.dto.PageDto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 当前用户的电商订单查询。
 */
public interface IOrderQueryService {

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
    PageDto<OrderViewDto> list(String userId, String platform, String orderStatus, String settleStatus, String keyword, int page, int size);

    /**
     * 汇总该用户一段时间的订单笔数和支付金额。
     *
     * @param userId 用户主键
     * @param from 开始时间，可空
     * @param to 结束时间，可空
     * @return 笔数 count 和支付金额 payAmount
     */
    Map<String, Object> stats(String userId, OffsetDateTime from, OffsetDateTime to);

    /**
     * 读取详情。不属于该用户时按不存在处理。
     *
     * @param userId 用户主键
     * @param id 订单主键
     * @return 订单详情
     */
    OrderDetailDto detail(String userId, String id);
}
