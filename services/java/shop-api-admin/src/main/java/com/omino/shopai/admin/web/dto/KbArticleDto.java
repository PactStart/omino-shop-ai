package com.omino.shopai.admin.web.dto;

/**
 * 知识库文章的写入字段。
 *
 * @param title 标题
 * @param body 正文
 * @param status 状态码，新建时忽略
 */
public record KbArticleDto(String title, String body, String status) {
}
