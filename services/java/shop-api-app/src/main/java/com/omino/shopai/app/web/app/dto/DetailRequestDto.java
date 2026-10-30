package com.omino.shopai.app.web.app.dto;

/**
 * 要看的商品。平台由商品号或字段决定。
 */
public record DetailRequestDto(String goodsId, String platform, String platformGoodsId) {
}
