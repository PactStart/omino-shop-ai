package com.omino.shopai.app.dto;
/**
 * 对话或列表里的商品卡。
 */

public record GoodsCardDto(
        String goodsId,
        String platform,
        String platformGoodsId,
        String title,
        String imageUrl,
        String price,
        String couponPrice,
        Long salesCount,
        Integer reviewCount,
        String goodsUrl,
        String shopName) {
}
