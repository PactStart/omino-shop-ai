package com.omino.shopai.admin.web.dto;

/**
 * 代发站内信的接收人和正文。
 */
public record InboxRequestDto(String userId, String title, String body) {
}
