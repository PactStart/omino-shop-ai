package com.omino.shopai.admin.service.agent;

import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.AgentPrompt;
import com.omino.shopai.mapper.AgentPromptMapper;
import com.omino.shopai.support.AgentPrompts;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
/**
 * AdminPromptService 编排用例，事务和校验放在这里。
 */

@Service
public class AdminPromptService {

    private final AgentPromptMapper prompts;

    public AdminPromptService(AgentPromptMapper prompts) {
        this.prompts = prompts;
    }

    public List<AgentPrompt> list() {
        return prompts.selectList(null);
    }
/**
 * 更新已有记录。
 */

    public AgentPrompt update(String key, String content, String updatedBy) {
        AgentPrompts.requireKey(key);
        String body = AgentPrompts.requireContent(content);
        AgentPrompt prompt = prompts.selectById(key);
        if (prompt == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "提示词不存在");
        }
        prompt.setContent(body);
        prompt.setUpdatedBy(updatedBy);
        prompt.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        prompts.updateById(prompt);
        return prompt;
    }
}
