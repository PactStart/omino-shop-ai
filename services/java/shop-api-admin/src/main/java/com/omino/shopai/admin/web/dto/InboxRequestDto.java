package com.omino.shopai.admin.web.dto;

import com.omino.shopai.enums.RichTextFormat;

/**
 * 代发站内信的接收人和正文。
 *
 * @param userId 接收人
 * @param title 标题
 * @param body 正文
 * @param format 纯文本或 HTML，可空则按纯文本
 */
public record InboxRequestDto(String userId, String title, String body, RichTextFormat format) {
}
