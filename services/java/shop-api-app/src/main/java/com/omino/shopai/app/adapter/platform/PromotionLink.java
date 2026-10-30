package com.omino.shopai.app.adapter.platform;

/**
 * 官方转链结果。只放可购买的链接，不放推广位、关系位或密钥。
 */
public record PromotionLink(String clickUrl, String deeplink, String universalLink, String fallbackHttpUrl) {
}
