package com.omino.shopai.admin.web.dto;

/**
 * 新建或修改金刚分类。
 *
 * @param name 分类名
 * @param sort 排序，小的在上
 * @param status 状态码
 */
public record MallCategoryRequestDto(String name, Integer sort, String status) {
}
