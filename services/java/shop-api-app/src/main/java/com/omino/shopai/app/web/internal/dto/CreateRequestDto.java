package com.omino.shopai.app.web.internal.dto;

/**
 * 建工单的分类和正文。
 */
public record CreateRequestDto(String category, String content, String orderId, String priority, String conversationId) {
}
