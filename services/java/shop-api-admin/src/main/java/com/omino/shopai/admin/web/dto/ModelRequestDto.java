package com.omino.shopai.admin.web.dto;

/**
 * 新建模型。密钥在服务里加密。
 */
public record ModelRequestDto(String name, String baseUrl, String modelName, String apiKey, int weight, int enabled, int timeoutMs) {
}
