package com.omino.shopai.app.service.goods;

import java.util.List;
/**
 * 一页商品。
 */

public record GoodsPage<T>(List<T> list, int page, int size, long total) {
}
