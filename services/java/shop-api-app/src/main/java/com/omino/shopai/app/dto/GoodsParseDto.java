package com.omino.shopai.app.dto;

/**
 * 万能解析给客户端的结果。
 *
 * @param goods 能落成商品卡时有值
 * @param url 解析出的链接，可空
 */
public record GoodsParseDto(GoodsCardDto goods, String url) {
}
