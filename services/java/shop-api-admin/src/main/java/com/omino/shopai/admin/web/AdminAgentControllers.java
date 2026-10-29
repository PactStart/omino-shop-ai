package com.omino.shopai.admin.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.admin.service.agent.AdminModelService;
import com.omino.shopai.admin.service.agent.AdminPromptService;
import com.omino.shopai.entity.AgentPrompt;
import com.omino.shopai.entity.LlmModel;
import com.omino.shopai.support.ModelSecret;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
/**
 * AdminAgentPromptController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "AdminAgentPromptController")
@RequestMapping("/v1/agent-prompts")
class AdminAgentPromptController {

    private final AdminPromptService prompts;

    AdminAgentPromptController(AdminPromptService prompts) {
        this.prompts = prompts;
    }

    @Operation(summary = "查询")
    @GetMapping
    ApiResponse<List<AgentPrompt>> list() {
        return ApiResponse.ok(prompts.list(), trace(), now());
    }

    @Operation(summary = "更新 /{key}")
    @PatchMapping("/{key}")
    ApiResponse<AgentPrompt> update(@PathVariable String key, @RequestBody PromptRequest request) {
        return ApiResponse.ok(prompts.update(key, request.content(), "admin"), trace(), now());
    }
/**
 * 更新一条提示词。键必须是八个固定键之一。
 */

    record PromptRequest(String content) {
    }

    private static String trace() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String now() {
        return OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString();
    }
}
/**
 * AdminLlmModelController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "AdminLlmModelController")
@RequestMapping("/v1/llm-models")
class AdminLlmModelController {

    private final AdminModelService models;

    AdminLlmModelController(AdminModelService models) {
        this.models = models;
    }

    @Operation(summary = "查询")
    @GetMapping
    ApiResponse<List<ModelSecret.ModelKeyView>> list() {
        return ApiResponse.ok(models.list(), trace(), now());
    }
/**
 * 创建新记录。
 */

    @Operation(summary = "提交")
    @PostMapping
    ApiResponse<ModelSecret.ModelKeyView> create(@RequestBody ModelRequest request) {
        LlmModel created = models.create(request.name(), request.baseUrl(), request.modelName(), request.apiKey(), request.weight(), request.enabled(), request.timeoutMs());
        return ApiResponse.ok(models.view(created.getId()), trace(), now());
    }
/**
 * 更新已有记录。
 */

    @Operation(summary = "更新 /{id}")
    @PatchMapping("/{id}")
    ApiResponse<ModelSecret.ModelKeyView> update(@PathVariable String id, @RequestBody ModelPatch request) {
        models.update(id, request.apiKey(), request.weight(), request.enabled(), request.timeoutMs());
        return ApiResponse.ok(models.view(id), trace(), now());
    }
/**
 * 新建模型。密钥在服务里加密。
 */

    record ModelRequest(String name, String baseUrl, String modelName, String apiKey, int weight, int enabled, int timeoutMs) {
    }
/**
 * 修改模型。密钥空着表示不换。
 */

    record ModelPatch(String apiKey, Integer weight, Integer enabled, Integer timeoutMs) {
    }

    private static String trace() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String now() {
        return OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString();
    }
}
