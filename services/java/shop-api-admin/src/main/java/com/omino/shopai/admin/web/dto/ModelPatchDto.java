package com.omino.shopai.admin.web.dto;

/**
 * 修改模型。密钥空着表示不换。
 */
public record ModelPatchDto(String apiKey, Integer weight, Integer enabled, Integer timeoutMs) {
}
