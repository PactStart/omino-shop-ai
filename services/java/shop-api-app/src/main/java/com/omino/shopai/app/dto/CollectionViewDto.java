package com.omino.shopai.app.dto;

import java.util.List;
/**
 * 合集和其中仍有效的商品。
 */

public record CollectionViewDto(String id, String title, List<GoodsCardDto> goods) {
}
