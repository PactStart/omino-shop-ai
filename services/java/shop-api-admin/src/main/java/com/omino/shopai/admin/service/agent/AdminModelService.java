package com.omino.shopai.admin.service.agent;

import com.omino.shopai.enums.OnOff;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.LlmModel;
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
/**
 * 注入模型目录和密钥材料。响应里不出现明文 Key。
 */

    public AdminModelService(LlmModelMapper models, LlmKeyCipher cipher) {
        this.models = models;
        this.cipher = cipher;
    }
/**
 * 按条件返回列表。
 */

    public List<ModelSecret.ModelKeyView> list() {
        List<ModelSecret.ModelKeyView> views = new ArrayList<>();
        for (LlmModel model : models.selectList(null)) {
            views.add(ModelSecret.view(model.getApiKeyEnc(), cipher));
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
 * 更新已有记录。
 */

    public LlmModel update(String id, String apiKey, Integer weight, Integer enabled, Integer timeoutMs) {
        LlmModel model = models.selectById(id);
        if (model == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "模型不存在");
        }
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
        return model;
    }
/**
 * 只返回是否已设置和掩码。明文留在密文列。
 */

    public ModelSecret.ModelKeyView view(String id) {
        LlmModel model = models.selectById(id);
        if (model == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "模型不存在");
        }
        return ModelSecret.view(model.getApiKeyEnc(), cipher);
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
