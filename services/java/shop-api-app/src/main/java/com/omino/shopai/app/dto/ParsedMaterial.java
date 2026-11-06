package com.omino.shopai.app.dto;

import com.omino.shopai.entity.GoodsSnapshot;

/**
 * 万能解析的结果。商品和链接可以只有其中一个。
 *
 * @param goods 能定位到的商品，可空
 * @param url 解析出的链接，可空
 */
public record ParsedMaterial(GoodsSnapshot goods, String url) {
}
