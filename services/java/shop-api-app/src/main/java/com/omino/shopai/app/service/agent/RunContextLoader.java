package com.omino.shopai.app.service.agent;

import com.omino.shopai.enums.OnOff;
import com.omino.shopai.app.service.chat.ChatRun;
import com.omino.shopai.app.service.chat.RunContext;
import com.omino.shopai.entity.LlmModel;
import com.omino.shopai.support.AgentPrompts;
import com.omino.shopai.support.LlmKeyCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
/**
 * 为一次调用准备提示词和模型。缺关键项则返回空。
 */

@Service
public class RunContextLoader implements com.omino.shopai.app.service.chat.RunContextLoader {

    private final AgentCatalog catalog;
    private final String keyMaterial;
    private final boolean local;
    private final Map<String, Holder> once = new ConcurrentHashMap<>();

    public RunContextLoader(
            AgentCatalog catalog,
            @Value("${LLM_CONFIG_KEY:}") String keyMaterial,
            @Value("${APP_ENV:local}") String env) {
        this.catalog = catalog;
        this.keyMaterial = keyMaterial;
        this.local = env == null || env.isBlank() || "local".equals(env);
    }

    @Override
    public RunContext load(ChatRun run) {
        return once.computeIfAbsent(run.runId(), id -> new Holder(build())).context;
    }
/**
 * 八个提示词必须齐全。local 可以没有模型，其它环境必须解开密钥。
 */

    private RunContext build() {
        Map<String, String> stored = catalog.prompts();
        Map<String, String> prompts = new LinkedHashMap<>();
        for (String key : AgentPrompts.KEYS) {
            String content = stored.get(key);
            // 八个键缺一个，整次调用不可用。
            if (content == null || content.isBlank()) {
                return null;
            }
            prompts.put(key, content.trim());
        }
        LlmModel model = choose(catalog.models());
        if (model == null) {
            // local 允许没有可分流模型；test 和 prod 必须选中。
            return local ? new RunContext(prompts, null) : null;
        }
        LlmKeyCipher cipher = cipher();
        if (cipher == null) {
            return null;
        }
        String apiKey = cipher.decrypt(model.getApiKeyEnc());
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        return new RunContext(prompts, new RunContext.LlmChoice(
                model.getId(),
                model.getBaseUrl(),
                model.getModelName(),
                apiKey,
                model.getTimeoutMs() == null ? 30000 : model.getTimeoutMs()));
    }
/**
 * 密钥材料不足 32 字节则不解密，也不因此让进程起不来。
 */

    private LlmKeyCipher cipher() {
        if (keyMaterial == null || keyMaterial.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            return null;
        }
        return new LlmKeyCipher(keyMaterial);
    }
/**
 * 只在启用且权重大于零的模型之间按权重抽取。
 */

    private static LlmModel choose(List<LlmModel> models) {
        List<LlmModel> candidates = new ArrayList<>();
        int weight = 0;
        for (LlmModel model : models) {
            if (model.getEnabled() != null && model.getEnabled() == OnOff.ON && model.getWeight() != null && model.getWeight() > 0) {
                candidates.add(model);
                weight += model.getWeight();
            }
        }
        if (candidates.isEmpty() || weight <= 0) {
            return null;
        }
        int pick = ThreadLocalRandom.current().nextInt(weight);
        int cursor = 0;
        for (LlmModel model : candidates) {
            cursor += model.getWeight();
            if (pick < cursor) {
                return model;
            }
        }
        return candidates.get(candidates.size() - 1);
    }
/**
 * 同一 run 只组装一次上下文，避免重复抽模型。
 */

    private record Holder(RunContext context) {
    }
}
