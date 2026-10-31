package com.omino.shopai.dto.order;

import com.omino.shopai.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 联盟拉回来的一笔订单，尚未落库。适配层可以构造它，不必依赖 app 的 service。
 *
 * @param platformOrderId 平台订单号
 * @param userId 归属用户，认不出则为空
 * @param parentUserId 上级用户，可空
 * @param goodsId 平台商品号
 * @param goodsTitle 商品标题
 * @param goodsImageUrl 商品图
 * @param payAmount 实付金额
 * @param mediaCommission 媒体佣金
 * @param orderStatus 本系统订单状态
 * @param orderedAt 下单时间
 * @param shippedAt 发货或完成时间
 */
public record IncomingOrderDto(
        String platformOrderId,
        String userId,
        String parentUserId,
        String goodsId,
        String goodsTitle,
        String goodsImageUrl,
        BigDecimal payAmount,
        BigDecimal mediaCommission,
        OrderStatus orderStatus,
        OffsetDateTime orderedAt,
        OffsetDateTime shippedAt) {
}
