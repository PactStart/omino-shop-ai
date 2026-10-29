package com.omino.shopai.app.service.goods;

import java.util.List;
/**
 * 合集和其中仍有效的商品。
 */

public record CollectionView(String id, String title, List<GoodsCard> goods) {
}
