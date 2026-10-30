package com.omino.shopai.app.service.agent;

import com.omino.shopai.entity.ChatSuggestion;
import com.omino.shopai.entity.LlmModel;

import java.util.List;
import java.util.Map;
/**
 * 提示词和模型的读取端口。
 */

public interface IAgentCatalog {

    Map<String, String> prompts();

    List<LlmModel> models();

    int activeSuggestionCount();

    void addSuggestion(ChatSuggestion suggestion);
}
