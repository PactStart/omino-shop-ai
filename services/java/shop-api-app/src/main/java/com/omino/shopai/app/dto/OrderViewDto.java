package com.omino.shopai.app.dto;
/**
 * 订单列表上的一行。不含媒体佣金。含这笔订单的推广身份。
 */

public record OrderViewDto(
        String id,
        String platform,
        String platformOrderId,
        String relationId,
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
