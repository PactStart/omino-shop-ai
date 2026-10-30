package com.omino.shopai.app.web.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.service.meta.InboxService;
import com.omino.shopai.entity.InboxMessage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
/**
 * InboxController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "InboxController")
@RequestMapping("/v1/inbox")
public class InboxController {

    private final InboxService inbox;

    public InboxController(InboxService inbox) {
        this.inbox = inbox;
    }
    /**
     * 列出当前用户的站内信。
     *
     * @param userId 当前用户
     * @return 站内信信封
     */
    @Operation(summary = "列出当前用户的站内信")
    @GetMapping
    public ApiResponse<List<InboxMessage>> list(@RequestAttribute("userId") String userId) {
        return ApiResponse.ok(inbox.list(userId));
    }
    /**
     * 统计当前用户未读站内信数量。
     *
     * @param userId 当前用户
     * @return 未读数量信封
     */
    @Operation(summary = "统计未读站内信")
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unread(@RequestAttribute("userId") String userId) {
        return ApiResponse.ok(Map.of("count", inbox.unread(userId)));
    }
    /**
     * 把一条站内信标为已读。
     *
     * @param userId 当前用户
     * @param id 站内信编号
     * @return 空信封
     */
    @Operation(summary = "把一条站内信标为已读")
    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(@RequestAttribute("userId") String userId, @PathVariable String id) {
        inbox.markRead(userId, id);
        return ApiResponse.ok(null);
    }


}
