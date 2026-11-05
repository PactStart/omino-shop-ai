package com.omino.shopai.app.service.agent;

import com.omino.shopai.app.service.chat.ChatRun;
import com.omino.shopai.app.service.chat.IRunContextLoader;
import com.omino.shopai.app.service.chat.RunContext;
import com.omino.shopai.entity.AgentPrompt;
import com.omino.shopai.entity.LlmModel;
import com.omino.shopai.enums.OnOff;
import com.omino.shopai.mapper.AgentPromptMapper;
import com.omino.shopai.mapper.LlmModelMapper;
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
 * 为一次调用准备提示词和模型。提示词缺行用默认正文补齐。test 和 prod 没有模型则返回空。
 */
@Service
public class RunContextLoaderImpl implements IRunContextLoader {

    private final AgentPromptMapper prompts;
    private final LlmModelMapper models;
    private final String keyMaterial;
    private final boolean local;
    private final Map<String, Holder> once = new ConcurrentHashMap<>();

    /**
     * 注入提示词和模型。密钥材料不足时不解密，也不因此让进程起不来。
     *
     * @param prompts 提示词表
     * @param models 模型表
     * @param keyMaterial 解密模型密钥的材料
     * @param env 当前环境，local 可以没有模型
     */
    public RunContextLoaderImpl(
            AgentPromptMapper prompts,
            LlmModelMapper models,
            @Value("${LLM_CONFIG_KEY:}") String keyMaterial,
            @Value("${APP_ENV:local}") String env) {
        this.prompts = prompts;
        this.models = models;
        this.keyMaterial = keyMaterial;
        this.local = env == null || env.isBlank() || "local".equals(env);
    }

    /**
     * 同一 run 只组装一次上下文，避免重复抽模型。
     *
     * @param run 这一轮
     * @return 提示词和模型；test 和 prod 没有模型则空
     */
    @Override
    public RunContext load(ChatRun run) {
        return once.computeIfAbsent(run.runId(), id -> new Holder(build())).context;
    }

    /**
     * 八个提示词缺行用默认正文补齐。local 可以没有模型，其它环境必须解开密钥。
     *
     * @return 提示词和模型；test 和 prod 没有模型则空
     */
    private RunContext build() {
        Map<String, String> ready = AgentPrompts.fill(loadPrompts());
        LlmModel model = choose(loadModels());
        if (model == null) {
            // local 允许没有可分流模型；test 和 prod 必须选中。
            return local ? new RunContext(ready, null) : null;
        }
        LlmKeyCipher cipher = cipher();
        if (cipher == null) {
            return null;
        }
        String apiKey = cipher.decrypt(model.getApiKeyEnc());
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        return new RunContext(ready, new RunContext.LlmChoice(
                model.getId(),
                model.getBaseUrl(),
                model.getModelName(),
                apiKey,
                model.getTimeoutMs() == null ? 30000 : model.getTimeoutMs()));
    }

    /**
     * 读出全部提示词，键对应正文。
     *
     * @return 提示词
     */
    protected Map<String, String> loadPrompts() {
        Map<String, String> rows = new LinkedHashMap<>();
        for (AgentPrompt prompt : prompts.selectList(null)) {
            rows.put(prompt.getKey(), prompt.getContent());
        }
        return rows;
    }

    /**
     * 读出全部模型，是否启用由调用方筛选。
     *
     * @return 模型
     */
    protected List<LlmModel> loadModels() {
        return models.selectList(null);
    }

    /**
     * 密钥材料不足 32 字节则不解密。
     *
     * @return 解密器；材料不够则空
     */
    private LlmKeyCipher cipher() {
        if (keyMaterial == null || keyMaterial.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            return null;
        }
        return new LlmKeyCipher(keyMaterial);
    }

    /**
     * 只在启用且权重大于零的模型之间按权重抽取。
     *
     * @param models 全部模型
     * @return 抽中的模型；没有候选人则空
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
     *
     * @param context 已组装的上下文，缺关键项时为空
     */
    private record Holder(RunContext context) {
    }
}
