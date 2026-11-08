package com.omino.shopai.app.dto;

import java.time.OffsetDateTime;

/**
 * 历史抽屉里的一条会话。不含消息。
 *
 * @param id 会话主键
 * @param title 标题，可空
 * @param updatedAt 最后更新时间
 */
public record ConversationViewDto(String id, String title, OffsetDateTime updatedAt) {
}
