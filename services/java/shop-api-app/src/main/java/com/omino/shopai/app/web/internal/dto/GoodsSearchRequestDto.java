package com.omino.shopai.app.web.internal.dto;

import java.util.List;

/**
 * 搜索词和平台。
 */
public record GoodsSearchRequestDto(String platform, String keyword, List<String> keywords, List<String> platforms) {
}
