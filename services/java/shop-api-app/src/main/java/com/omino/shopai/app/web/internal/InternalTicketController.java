package com.omino.shopai.app.web.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.service.ticket.ITicketService;
import com.omino.shopai.entity.Ticket;
import com.omino.shopai.app.web.internal.dto.CreateRequestDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * InternalTicketController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "InternalTicketController")
@RequestMapping("/internal/v1/tickets")
public class InternalTicketController {

    private final ITicketService tickets;

    /**
     * 注入工单用例。
     *
     * @param tickets 建单用例
     */
    public InternalTicketController(ITicketService tickets) {
        this.tickets = tickets;
    }
    /**
     * 用用户反馈建立一条工单。
     *
     * @param userId 当前对话用户
     * @param request 分类、内容和关联订单
     * @return 工单信封
     */
    @Operation(summary = "建立一条工单")
    @PostMapping
    public ApiResponse<Ticket> create(@RequestHeader("X-User-Id") String userId, @RequestBody CreateRequestDto request) {
        return ApiResponse.ok(
                tickets.create(userId, request.category(), request.content(), request.orderId(), request.priority(), request.conversationId()));
    }
}
