package com.omino.shopai.app.dto;
/**
 * 订单列表上的一行。不含媒体佣金。
 */

public record OrderViewDto(
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
