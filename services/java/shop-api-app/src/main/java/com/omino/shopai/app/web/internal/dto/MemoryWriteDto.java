package com.omino.shopai.app.web.internal.dto;

import java.math.BigDecimal;

/**
 * 记忆节点写入的一条语义记忆。
 */
public record MemoryWriteDto(String kind, String text, BigDecimal confidence, String source, String supersedeId) {
}
