package com.omino.shopai.app.dto;

import java.util.List;
/**
 * 商品详情。价格是字符串。
 */

public record GoodsDetailDto(
        String goodsId,
        String platform,
        String platformGoodsId,
        String title,
        String subtitle,
        List<String> imageUrls,
        String price,
        String couponPrice,
        String commissionHint,
        String shopName,
        Long salesCount,
        Integer reviewCount,
        String goodsUrl,
        String detailHtml,
        String skuSummary,
        boolean canPromote) {
}
