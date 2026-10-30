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

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    @Operation(summary = "查询")
    @GetMapping
    public ApiResponse<List<InboxMessage>> list(@RequestAttribute("userId") String userId) {
        return ApiResponse.ok(inbox.list(userId), trace(), now());
    }

    @Operation(summary = "查询 /unread-count")
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unread(@RequestAttribute("userId") String userId) {
        return ApiResponse.ok(Map.of("count", inbox.unread(userId)), trace(), now());
    }
/**
 * 把一条站内信标成已读。
 */

    @Operation(summary = "提交 /{id}/read")
    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(@RequestAttribute("userId") String userId, @PathVariable String id) {
        inbox.markRead(userId, id);
        return ApiResponse.ok(null, trace(), now());
    }

    private static String trace() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String now() {
        return OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString();
    }
}
