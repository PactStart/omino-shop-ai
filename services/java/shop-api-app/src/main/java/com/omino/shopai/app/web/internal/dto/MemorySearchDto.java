package com.omino.shopai.app.web.internal.dto;

/**
 * 检索记忆时的条数。空则按 20 条。
 */
public record MemorySearchDto(Integer limit) {
}
