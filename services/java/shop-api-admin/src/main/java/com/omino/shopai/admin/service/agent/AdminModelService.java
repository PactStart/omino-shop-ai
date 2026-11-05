package com.omino.shopai.admin.service.agent;

import com.omino.shopai.admin.web.dto.LlmModelViewDto;
import com.omino.shopai.enums.OnOff;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.AdminAuditLog;
import com.omino.shopai.entity.LlmModel;
import com.omino.shopai.mapper.AdminAuditLogMapper;
import com.omino.shopai.mapper.LlmModelMapper;
import com.omino.shopai.support.Ids;
import com.omino.shopai.support.LlmKeyCipher;
import com.omino.shopai.support.ModelSecret;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
/**
 * AdminModelService 编排用例，事务和校验放在这里。
 */

@Service
public class AdminModelService {

    private final LlmModelMapper models;
    private final LlmKeyCipher cipher;
    private final AdminAuditLogMapper audits;

    /**
     * 注入模型目录、密钥材料和审计。响应里不出现明文 Key。
     *
     * @param models 模型表
     * @param cipher 密钥加密
     * @param audits 审计表
     */
    public AdminModelService(LlmModelMapper models, LlmKeyCipher cipher, AdminAuditLogMapper audits) {
        this.models = models;
        this.cipher = cipher;
        this.audits = audits;
    }

    /**
     * 列出模型。密钥只有末四位或全掩码。
     *
     * @return 模型列表
     */
    public List<LlmModelViewDto> list() {
        List<LlmModelViewDto> views = new ArrayList<>();
        for (LlmModel model : models.selectList(null)) {
            views.add(present(model));
        }
        return views;
    }
/**
 * 创建新记录。
 */

    public LlmModel create(String name, String baseUrl, String modelName, String apiKey, int weight, int enabled, int timeoutMs) {
        if (models.selectCount(new LambdaQueryWrapper<LlmModel>().eq(LlmModel::getName, name)) > 0) {
            rejectDuplicate(1);
        }
        LlmModel model = new LlmModel();
        model.setId(Ids.ulid());
        model.setName(name);
        model.setBaseUrl(baseUrl);
        model.setModelName(modelName);
        model.setApiKeyEnc(cipher.encrypt(apiKey));
        model.setWeight(weight);
        model.setEnabled(OnOff.fromCode(enabled));
        model.setTimeoutMs(timeoutMs);
        model.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        model.setUpdatedAt(model.getCreatedAt());
        models.insert(model);
        return model;
    }
    /**
     * 修改权重、开关或超时。密钥留空则保持原密钥，并写一条不含密钥的审计。
     *
     * @param id 模型主键
     * @param apiKey 新密钥，可空
     * @param weight 新权重，可空
     * @param enabled 0 或 1，可空
     * @param timeoutMs 超时毫秒，可空
     * @return 更新后的模型
     */
    public LlmModel update(String id, String apiKey, Integer weight, Integer enabled, Integer timeoutMs) {
        LlmModel model = models.selectById(id);
        if (model == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "模型不存在");
        }
        Integer weightBefore = model.getWeight();
        byte[] next = ModelSecret.keepOrReplace(model.getApiKeyEnc(), apiKey, cipher);
        model.setApiKeyEnc(next);
        if (weight != null) {
            model.setWeight(weight);
        }
        if (enabled != null) {
            model.setEnabled(OnOff.fromCode(enabled));
        }
        if (timeoutMs != null) {
            model.setTimeoutMs(timeoutMs);
        }
        model.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        models.updateById(model);
        AdminAuditLog audit = new AdminAuditLog();
        audit.setId(Ids.ulid());
        audit.setSsoSub("admin");
        audit.setAction("llm_model.update");
        audit.setResource(id);
        audit.setDetail("{\"weightBefore\":" + weightBefore + ",\"weightAfter\":" + model.getWeight() + "}");
        audit.setCreatedAt(model.getUpdatedAt());
        audits.insertJson(audit);
        return model;
    }

    /**
     * 按文档返回一行。不含密钥明文。
     *
     * @param id 模型主键
     * @return 掩码后的模型
     */
    public LlmModelViewDto view(String id) {
        LlmModel model = models.selectById(id);
        if (model == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "模型不存在");
        }
        return present(model);
    }

    private LlmModelViewDto present(LlmModel model) {
        ModelSecret.ModelKeyView secret = ModelSecret.view(model.getApiKeyEnc(), cipher);
        int enabled = model.getEnabled() == null ? 0 : model.getEnabled().code();
        return new LlmModelViewDto(
                model.getId(),
                model.getName(),
                model.getBaseUrl(),
                model.getModelName(),
                model.getWeight(),
                enabled,
                model.getTimeoutMs(),
                secret.apiKeySet(),
                secret.apiKeyMask(),
                model.getUpdatedAt());
    }
/**
 * 拒绝并解开冻结。
 */

    public static void rejectDuplicate(long count) {
        if (count > 0) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "模型名称已存在", "name", null);
        }
    }
}
