package com.omino.shopai.app.web.app.dto;

/**
 * 猜你喜欢的平台和分页。
 */
public record GoodsGuessRequestDto(String platform, Integer page, Integer size) {
}
