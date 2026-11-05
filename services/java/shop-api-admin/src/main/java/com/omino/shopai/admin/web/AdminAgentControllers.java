package com.omino.shopai.admin.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.admin.web.dto.ModelPatchDto;
import com.omino.shopai.admin.web.dto.ModelRequestDto;
import com.omino.shopai.admin.web.dto.PromptRequestDto;
import com.omino.shopai.admin.service.agent.AdminModelService;
import com.omino.shopai.admin.service.agent.AdminPromptService;
import com.omino.shopai.admin.web.dto.LlmModelViewDto;
import com.omino.shopai.entity.AgentPrompt;
import com.omino.shopai.entity.LlmModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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

    /**
     * 列出已有的八条提示词。
     *
     * @return 提示词列表
     */
    @Operation(summary = "列出提示词")
    @GetMapping
    ApiResponse<List<AgentPrompt>> list() {
        return ApiResponse.ok(prompts.list());
    }

    /**
     * 修改一条已有提示词的正文。不能新增或改名。
     *
     * @param key 提示词键
     * @param request 新正文
     * @return 修改后的提示词
     */
    @Operation(summary = "修改一条提示词正文")
    @PatchMapping("/{key}")
    ApiResponse<AgentPrompt> update(@PathVariable String key, @RequestBody PromptRequestDto request) {
        return ApiResponse.ok(prompts.update(key, request.content(), "admin"));
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

    /**
     * 列出模型。密钥只出现末四位或全掩码。
     *
     * @return 模型列表
     */
    @Operation(summary = "列出模型")
    @GetMapping
    ApiResponse<List<LlmModelViewDto>> list() {
        return ApiResponse.ok(models.list());
    }
    /**
     * 登记一个模型。密钥只保存在服务端。
     *
     * @param request 名称、地址、模型名、密钥和权重
     * @return 掩码后的模型
     */
    @Operation(summary = "登记一个模型")
    @PostMapping
    ApiResponse<LlmModelViewDto> create(@RequestBody ModelRequestDto request) {
        LlmModel created = models.create(request.name(), request.baseUrl(), request.modelName(), request.apiKey(), request.weight(), request.enabled(), request.timeoutMs());
        return ApiResponse.ok(models.view(created.getId()));
    }
    /**
     * 修改权重、开关或超时。密钥留空表示不改。
     *
     * @param id 模型主键
     * @param request 要改的字段
     * @return 掩码后的模型
     */
    @Operation(summary = "修改模型权重或开关")
    @PatchMapping("/{id}")
    ApiResponse<LlmModelViewDto> update(@PathVariable String id, @RequestBody ModelPatchDto request) {
        models.update(id, request.apiKey(), request.weight(), request.enabled(), request.timeoutMs());
        return ApiResponse.ok(models.view(id));
    }
}
