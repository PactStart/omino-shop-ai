package com.omino.shopai.app.service.chat;

import java.util.Map;
/**
 * 这一次调 Agent 要用的提示词和可选模型。
 */

public record RunContext(Map<String, String> prompts, LlmChoice llm) {

    public static RunContext passthrough() {
        return new RunContext(Map.of(), null);
    }
/**
 * 这一次调用选中的模型。明文 Key 只活在这次上下文里。
 */

    public record LlmChoice(String id, String baseUrl, String modelName, String apiKey, int timeoutMs) {
    }
}
