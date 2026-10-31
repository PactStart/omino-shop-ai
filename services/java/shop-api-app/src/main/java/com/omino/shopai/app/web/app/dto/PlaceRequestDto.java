package com.omino.shopai.app.web.app.dto;

/**
 * 购买积分。渠道只允许微信、支付宝或钱包。
 */
public record PlaceRequestDto(String packageId, String channel, String clientOrderId) {
}
