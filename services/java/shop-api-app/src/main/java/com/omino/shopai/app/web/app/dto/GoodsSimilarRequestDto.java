package com.omino.shopai.app.web.app.dto;

/**
 * 相似商品的种子和分页。
 */
public record GoodsSimilarRequestDto(
        String goodsId, String platform, String platformGoodsId, Integer page, Integer size) {
}
