package com.omino.shopai.admin.web;

import com.omino.shopai.admin.service.ops.AdminConversationService;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.dto.PageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 会话观测。只读。
 */
@RestController
@Tag(name = "会话")
@RequestMapping("/v1/conversations")
public class AdminConversationController {

    private final AdminConversationService conversations;

    /**
     * 注入会话查询。
     *
     * @param conversations 会话用例
     */
    public AdminConversationController(AdminConversationService conversations) {
        this.conversations = conversations;
    }

    /**
     * 分页查询会话。
     *
     * @param userId 用户主键，可空
     * @param page 页码
     * @param size 页大小
     * @return 会话页
     */
    @Operation(summary = "分页查询会话")
    @GetMapping
    public ApiResponse<PageDto<Map<String, Object>>> list(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(conversations.list(userId, page, size));
    }

    /**
     * 按时间升序读取一个会话的消息。
     *
     * @param id 会话主键
     * @return 消息列表
     */
    @Operation(summary = "按时间查看会话消息")
    @GetMapping("/{id}/messages")
    public ApiResponse<List<Map<String, Object>>> messages(@PathVariable String id) {
        return ApiResponse.ok(conversations.messages(id));
    }
}
