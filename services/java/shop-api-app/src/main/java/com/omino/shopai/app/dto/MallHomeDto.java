package com.omino.shopai.app.dto;

/**
 * 商城首页里可配置的两块。搜索和猜你喜欢不在这里。
 *
 * @param banner 轮播
 * @param nav 金刚区
 */
public record MallHomeDto(MallBannerDto banner, MallNavDto nav) {
}
