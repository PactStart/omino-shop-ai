package com.omino.shopai.app.dto;

import java.util.List;

/**
 * 商城轮播。
 *
 * @param intervalMs 自动切换间隔，0 表示不自动播
 * @param items 按排序排好的图
 */
public record MallBannerDto(int intervalMs, List<MallItemDto> items) {
}
