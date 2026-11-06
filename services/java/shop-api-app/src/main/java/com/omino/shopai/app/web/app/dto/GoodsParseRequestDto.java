package com.omino.shopai.app.web.app.dto;

/**
 * 要解析的一段链接或口令。
 */
public record GoodsParseRequestDto(String platform, String content) {
}
