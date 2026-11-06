package com.omino.shopai.app.dto;

import java.util.List;

/**
 * 商城金刚区。
 *
 * @param columns 每行图标个数
 * @param categories 有图标的分类
 */
public record MallNavDto(int columns, List<MallCategoryDto> categories) {
}
