package com.omino.shopai.admin.web.dto;

import java.time.OffsetDateTime;

/**
 * 模型列表和保存后的一行。不含密钥明文。
 */
public record LlmModelViewDto(
        String id,
        String name,
        String baseUrl,
        String modelName,
        Integer weight,
        Integer enabled,
        Integer timeoutMs,
        boolean apiKeySet,
        String apiKeyMask,
        OffsetDateTime updatedAt) {
}
