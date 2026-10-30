package com.omino.shopai.dto.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 联盟拉回来的一笔订单，尚未落库。适配层可以构造它，不必依赖 app 的 service。
 */
public record IncomingOrder(
        String platformOrderId,
        String userId,
        String parentUserId,
        String goodsId,
        String goodsTitle,
        String goodsImageUrl,
        BigDecimal payAmount,
        BigDecimal mediaCommission,
        String orderStatus,
        OffsetDateTime orderedAt,
        OffsetDateTime shippedAt) {
}
