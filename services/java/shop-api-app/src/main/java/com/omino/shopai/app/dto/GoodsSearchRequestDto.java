package com.omino.shopai.app.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 商城搜索。页码、条数、排序、价格、品牌、有券和翻页游标都可以不传。
 *
 * @param platform 平台码
 * @param keyword 关键词
 * @param page 页码，从 1 起
 * @param size 每页条数
 * @param sort 排序，可空
 * @param priceMin 最低价，单位元，可空
 * @param priceMax 最高价，单位元，可空
 * @param brand 只要品牌商品；只有 true 才筛选
 * @param coupon 只要有券商品；只有 true 才筛选
 * @param nextId 上一页返回的翻页游标，可空
 */
public record GoodsSearchRequestDto(
        String platform,
        String keyword,
        Integer page,
        Integer size,
        String sort,
        String priceMin,
        String priceMax,
        Boolean brand,
        Boolean coupon,
        String nextId) {

    /**
     * 对话搜品的第一页，不带筛选。
     *
     * @param platform 平台码
     * @param keyword 关键词
     * @return 只要第一页的请求
     */
    public static GoodsSearchRequestDto firstPage(String platform, String keyword) {
        return new GoodsSearchRequestDto(platform, keyword, 1, 20, null, null, null, null, null, null);
    }

    /**
     * 把已校验的金额换成元，给只收小数的平台。空金额返回空。
     *
     * @param amount 单位元的金额
     * @return 元；空输入返回空
     */
    public static Double yuan(String amount) {
        if (amount == null) {
            return null;
        }
        return Double.valueOf(amount);
    }

    /**
     * 把已校验的金额收成整数元，给只收整数的平台。
     *
     * @param amount 单位元的金额
     * @return 四舍五入后的元；空输入返回空
     */
    public static Long yuanWhole(String amount) {
        if (amount == null) {
            return null;
        }
        return new BigDecimal(amount).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /**
     * 把已校验的金额换成分。
     *
     * @param amount 单位元的金额
     * @return 分；空输入返回空
     */
    public static Long fen(String amount) {
        if (amount == null) {
            return null;
        }
        return new BigDecimal(amount).movePointRight(2).longValueExact();
    }
}
