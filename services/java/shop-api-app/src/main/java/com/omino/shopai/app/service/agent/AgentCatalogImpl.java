package com.omino.shopai.app.service.agent;

import com.omino.shopai.enums.ActiveStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.entity.AgentPrompt;
import com.omino.shopai.entity.ChatSuggestion;
import com.omino.shopai.entity.LlmModel;
import com.omino.shopai.mapper.AgentPromptMapper;
import com.omino.shopai.mapper.ChatSuggestionMapper;
import com.omino.shopai.mapper.LlmModelMapper;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * 持久化边界。事务和状态判断由调用方负责。
 */

@Repository
public class AgentCatalogImpl implements IAgentCatalog {

    private final AgentPromptMapper prompts;
    private final LlmModelMapper models;
    private final ChatSuggestionMapper suggestions;
/**
 * 提示词、模型和开场建议的 MyBatis 访问。
 */

    public AgentCatalogImpl(AgentPromptMapper prompts, LlmModelMapper models, ChatSuggestionMapper suggestions) {
        this.prompts = prompts;
        this.models = models;
        this.suggestions = suggestions;
    }

    @Override
    public Map<String, String> prompts() {
        Map<String, String> rows = new LinkedHashMap<>();
        for (AgentPrompt prompt : prompts.selectList(null)) {
            rows.put(prompt.getKey(), prompt.getContent());
        }
        return rows;
    }

    @Override
    public List<LlmModel> models() {
        return models.selectList(null);
    }
/**
 * 数出生效建议，添加前用来卡住上限。
 */

    @Override
    public int activeSuggestionCount() {
        Long count = suggestions.selectCount(new LambdaQueryWrapper<ChatSuggestion>().eq(ChatSuggestion::getStatus, ActiveStatus.ACTIVE));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public void addSuggestion(ChatSuggestion suggestion) {
        suggestions.insert(suggestion);
    }
}
