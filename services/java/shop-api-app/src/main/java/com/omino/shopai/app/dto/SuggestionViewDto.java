package com.omino.shopai.app.dto;

/**
 * 空对话上的一条推荐问题。
 *
 * @param id 推荐主键
 * @param text 点下去就当作普通消息发送的文案
 */
public record SuggestionViewDto(String id, String text) {
}
