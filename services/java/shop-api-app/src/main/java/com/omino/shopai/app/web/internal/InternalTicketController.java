package com.omino.shopai.app.web.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.service.ticket.TicketService;
import com.omino.shopai.entity.Ticket;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
/**
 * InternalTicketController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "InternalTicketController")
@RequestMapping("/internal/v1/tickets")
public class InternalTicketController {

    private final TicketService tickets;

    public InternalTicketController(TicketService tickets) {
        this.tickets = tickets;
    }

    @Operation(summary = "提交")
    @PostMapping
    public ApiResponse<Ticket> create(@RequestHeader("X-User-Id") String userId, @RequestBody CreateRequest request) {
        return ApiResponse.ok(
                tickets.create(userId, request.category(), request.content(), request.orderId(), request.priority(), request.conversationId()),
                trace(),
                now());
    }

    private static String trace() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String now() {
        return OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString();
    }
/**
 * 建工单的分类和正文。
 */

    public record CreateRequest(String category, String content, String orderId, String priority, String conversationId) {
    }
}
