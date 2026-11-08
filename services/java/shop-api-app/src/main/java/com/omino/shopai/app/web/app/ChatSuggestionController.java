package com.omino.shopai.app.web.app;

import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.dto.SuggestionViewDto;
import com.omino.shopai.app.service.agent.IAgentConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 空对话的推荐问题。
 */
@RestController
@Tag(name = "ChatSuggestionController")
@RequestMapping("/v1/chat/suggestions")
public class ChatSuggestionController {

    private final IAgentConfigService suggestions;

    /**
     * 注入推荐用例。
     *
     * @param suggestions 推荐用例
     */
    public ChatSuggestionController(IAgentConfigService suggestions) {
        this.suggestions = suggestions;
    }

    /**
     * 列出当前生效的推荐问题。
     *
     * @return 推荐列表信封
     */
    @Operation(summary = "列出空对话的推荐问题")
    @GetMapping
    public ApiResponse<List<SuggestionViewDto>> list() {
        return ApiResponse.ok(suggestions.active());
    }
}