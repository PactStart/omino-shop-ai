package com.omino.shopai.admin.web;

import com.omino.shopai.admin.service.ticket.AdminTicketService;
import com.omino.shopai.admin.web.dto.TicketChangeDto;
import com.omino.shopai.admin.web.dto.TicketReplyDto;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.dto.PageDto;
import com.omino.shopai.entity.Ticket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 工单列表、回复和改派。
 */
@RestController
@Tag(name = "工单")
@RequestMapping("/v1/tickets")
public class AdminTicketController {

    private final AdminTicketService tickets;

    /**
     * 注入工单用例。
     *
     * @param tickets 工单服务
     */
    public AdminTicketController(AdminTicketService tickets) {
        this.tickets = tickets;
    }

    /**
     * 分页查询工单。
     *
     * @param userId 用户主键，可空
     * @param status 状态码，可空
     * @param page 页码
     * @param size 页大小
     * @return 工单页
     */
    @Operation(summary = "分页查询工单")
    @GetMapping
    public ApiResponse<PageDto<Ticket>> list(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(tickets.list(userId, status, page, size));
    }

    /**
     * 查看工单和它的处理记录。
     *
     * @param id 工单主键
     * @return 工单与时间线
     */
    @Operation(summary = "查看工单和时间线")
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String id) {
        return ApiResponse.ok(tickets.detail(id));
    }

    /**
     * 追加一条回复，不改变工单状态。
     *
     * @param request 当前管理会话
     * @param id 工单主键
     * @param body 回复正文
     * @return 状态未变的工单
     */
    @Operation(summary = "回复工单")
    @PostMapping("/{id}/reply")
    public ApiResponse<Ticket> reply(HttpServletRequest request, @PathVariable String id, @RequestBody TicketReplyDto body) {
        return ApiResponse.ok(tickets.reply(id, body.content(), sub(request)));
    }

    /**
     * 修改状态、优先级或处理人。
     *
     * @param request 当前管理会话
     * @param id 工单主键
     * @param body 要改的字段
     * @return 更新后的工单
     */
    @Operation(summary = "修改工单状态或处理人")
    @PatchMapping("/{id}")
    public ApiResponse<Ticket> change(HttpServletRequest request, @PathVariable String id, @RequestBody TicketChangeDto body) {
        return ApiResponse.ok(tickets.change(id, body.status(), body.priority(), body.assigneeSsoSub(), sub(request)));
    }

    private static String sub(HttpServletRequest request) {
        Object value = request.getSession(false) == null ? null : request.getSession(false).getAttribute("ssoSub");
        return value == null ? "admin" : value.toString();
    }
}
