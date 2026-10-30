package com.omino.shopai.app.dto;
/**
 * 转链结果。deeplink 由服务端签发。
 */

public record ConvertedLinkDto(
        String goodsId,
        String platform,
        String clickUrl,
        String deeplink,
        String universalLink,
        String fallbackHttpUrl,
        String expiresAt) {
}
