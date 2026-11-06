package com.omino.shopai.app.web.app.dto;

/**
 * 要生成口令的商品。
 */
public record GoodsCommandRequestDto(String goodsId, String platform, String platformGoodsId) {
}
