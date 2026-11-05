package com.omino.shopai.admin.web.dto;

/**
 * 推荐问题的文案、排序和上下架。空字段表示这次不改。
 *
 * @param text 文案
 * @param sort 排序
 * @param status 状态码
 */
public record SuggestionRequestDto(String text, Integer sort, String status) {
}
