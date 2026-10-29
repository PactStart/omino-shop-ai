package com.omino.shopai.app.web.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.service.order.OrderDetail;
import com.omino.shopai.app.service.order.OrderPage;
import com.omino.shopai.app.service.order.OrderQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
/**
 * OrderController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "OrderController")
@RequestMapping("/v1/orders")
public class OrderController {

    private final OrderQueryService orders;

    public OrderController(OrderQueryService orders) {
        this.orders = orders;
    }

    @Operation(summary = "查询")
    @GetMapping
    public ApiResponse<OrderPage> list(
            @RequestAttribute("userId") String userId,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) String settleStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(orders.list(userId, platform, orderStatus, settleStatus, page, size), traceId(), now());
    }

    @Operation(summary = "查询 /{id}")
    @GetMapping("/{id}")
    public ApiResponse<OrderDetail> detail(@RequestAttribute("userId") String userId, @PathVariable String id) {
        return ApiResponse.ok(orders.detail(userId, id), traceId(), now());
    }

    private static String traceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String now() {
        return OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString();
    }
}
