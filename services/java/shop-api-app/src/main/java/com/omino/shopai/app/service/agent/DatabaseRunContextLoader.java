package com.omino.shopai.app.service.agent;

import com.omino.shopai.enums.OnOff;
import com.omino.shopai.app.service.chat.ChatRun;
import com.omino.shopai.app.service.chat.RunContext;
import com.omino.shopai.app.service.chat.RunContextLoader;
import com.omino.shopai.entity.LlmModel;
import com.omino.shopai.support.AgentPrompts;
import com.omino.shopai.support.LlmKeyCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
/**
 * 从库里装提示词和模型。缺提示词则这次调用不可用。
 */

@Service
public class DatabaseRunContextLoader implements RunContextLoader {

    private final AgentCatalog catalog;
    private final LlmKeyCipher cipher;
    private final boolean local;
/**
 * 按当前 profile 决定缺模型时是否允许继续。
 */

    public DatabaseRunContextLoader(
            AgentCatalog catalog,
            LlmKeyCipher cipher,
            @Value("${spring.profiles.active:local}") String profile) {
        this.catalog = catalog;
        this.cipher = cipher;
        this.local = profile.equals("local") || profile.startsWith("local,");
    }
/**
 * 装载调用所需的数据。
 */

    @Override
    public RunContext load(ChatRun run) {
        Map<String, String> stored = catalog.prompts();
        Map<String, String> prompts = new LinkedHashMap<>();
        for (String key : AgentPrompts.KEYS) {
            String content = stored.get(key);
            if (content == null || content.isBlank()) {
                return null;
            }
            prompts.put(key, content);
        }
        RunContext.LlmChoice choice = pick(catalog.models());
        if (choice == null && !local) {
            return null;
        }
        return new RunContext(prompts, choice);
    }
/**
 * 按权重抽一个启用模型，并解开密钥。解不开则这次没有模型。
 */

    private RunContext.LlmChoice pick(List<LlmModel> models) {
        // 只在启用且权重大于零的模型之间抽取。
        List<LlmModel> candidates = new ArrayList<>();
        int total = 0;
        for (LlmModel model : models) {
            int weight = model.getWeight() == null ? 0 : model.getWeight();
            if (model.getEnabled() != null && model.getEnabled() == OnOff.ON && weight > 0) {
                candidates.add(model);
                total += weight;
            }
        }
        if (candidates.isEmpty() || total <= 0) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        int cursor = 0;
        LlmModel chosen = candidates.get(0);
        for (LlmModel model : candidates) {
            cursor += model.getWeight();
            if (roll < cursor) {
                chosen = model;
                break;
            }
        }
        String apiKey = cipher.decrypt(chosen.getApiKeyEnc());
        return new RunContext.LlmChoice(
                chosen.getId(),
                chosen.getBaseUrl(),
                chosen.getModelName(),
                apiKey,
                chosen.getTimeoutMs() == null ? 30000 : chosen.getTimeoutMs());
    }
}
