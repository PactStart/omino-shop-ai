package com.omino.shopai.app.service.goods;
/**
 * 转链结果。deeplink 由服务端签发。
 */

public record ConvertedLink(
        String goodsId,
        String platform,
        String clickUrl,
        String deeplink,
        String universalLink,
        String fallbackHttpUrl,
        String expiresAt) {
}
