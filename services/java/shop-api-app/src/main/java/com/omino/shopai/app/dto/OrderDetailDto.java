package com.omino.shopai.app.dto;
/**
 * 订单详情。仍不返回媒体佣金和上级用户。
 */

public record OrderDetailDto(
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
        String shippedAt,
        String logistics) {
}
