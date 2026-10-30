package com.omino.shopai.app.service.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
/**
 * 联盟拉回来的一笔订单，尚未落库。
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
