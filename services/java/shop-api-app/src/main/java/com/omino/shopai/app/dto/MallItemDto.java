package com.omino.shopai.app.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 轮播图或金刚图标。
 *
 * @param id 条目主键
 * @param title 标题，轮播可空
 * @param imageUrl 可直接展示的图片地址
 * @param actionType 点击类型
 * @param action 点击参数
 */
public record MallItemDto(String id, String title, String imageUrl, String actionType, JsonNode action) {
}
