package com.omino.shopai.app.service.order;
/**
 * 订单列表上的一行。不含媒体佣金。
 */

public record OrderView(
        String id,
        String platform,
        String goodsId,
        String goodsTitle,
        String goodsImageUrl,
        String payAmount,
        String userCommission,
        String orderStatus,
        String settleStatus,
        String orderedAt,
        String shippedAt) {
}
