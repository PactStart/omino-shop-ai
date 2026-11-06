package com.omino.shopai.app.dto;

import java.util.List;

/**
 * 金刚区里的一个分类和它下面的图标。
 *
 * @param id 分类主键
 * @param name 分类名
 * @param items 该分类下的图标
 */
public record MallCategoryDto(String id, String name, List<MallItemDto> items) {
}
