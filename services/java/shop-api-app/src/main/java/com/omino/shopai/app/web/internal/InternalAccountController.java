package com.omino.shopai.app.web.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.service.auth.InternalAccountService;
import com.omino.shopai.app.service.credit.CreditBillingService;
import com.omino.shopai.app.service.order.OrderDetail;
import com.omino.shopai.app.service.order.OrderPage;
import com.omino.shopai.app.service.order.OrderQueryService;
import com.omino.shopai.app.service.wallet.WalletView;
import com.omino.shopai.app.service.wallet.WithdrawalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
/**
 * InternalAccountController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "InternalAccountController")
@RequestMapping("/internal/v1")
public class InternalAccountController {

    private final InternalAccountService accounts;
    private final OrderQueryService orders;
    private final WithdrawalService wallet;
    private final CreditBillingService credits;
/**
 * Agent 工具的账号入口。调用方必须带当前用户。
 */

    public InternalAccountController(
            InternalAccountService accounts,
            OrderQueryService orders,
            WithdrawalService wallet,
            CreditBillingService credits) {
        this.accounts = accounts;
        this.orders = orders;
        this.wallet = wallet;
        this.credits = credits;
    }

    @Operation(summary = "查询 /me")
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@RequestHeader("X-User-Id") String userId) {
        return ApiResponse.ok(accounts.me(userId), trace(), now());
    }

    @Operation(summary = "查询 /me/authorizations")
    @GetMapping("/me/authorizations")
    public ApiResponse<List<Map<String, Object>>> authorizations(@RequestHeader("X-User-Id") String userId) {
        return ApiResponse.ok(accounts.authorizations(userId), trace(), now());
    }

    @Operation(summary = "提交 /me/authorizations/start")
    @PostMapping("/me/authorizations/start")
    public ApiResponse<Map<String, String>> start(@RequestBody PlatformRequest request) {
        return ApiResponse.ok(accounts.startAuthorization(request.platform()), trace(), now());
    }

    @Operation(summary = "提交 /me/authorizations/revoke")
    @PostMapping("/me/authorizations/revoke")
    public ApiResponse<Map<String, String>> revoke(@RequestHeader("X-User-Id") String userId, @RequestBody PlatformRequest request) {
        return ApiResponse.ok(accounts.revokeAuthorization(userId, request.platform()), trace(), now());
    }

    @Operation(summary = "查询 /orders")
    @GetMapping("/orders")
    public ApiResponse<OrderPage> orders(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "orderStatus", required = false) String orderStatus,
            @RequestParam(value = "settleStatus", required = false) String settleStatus,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(orders.list(userId, platform, orderStatus, settleStatus, page, size), trace(), now());
    }

    @Operation(summary = "查询 /orders/stats")
    @GetMapping("/orders/stats")
    public ApiResponse<Map<String, Object>> stats(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        return ApiResponse.ok(orders.stats(userId, parse(from), parse(to)), trace(), now());
    }

    @Operation(summary = "查询 /orders/{id}")
    @GetMapping("/orders/{id}")
    public ApiResponse<OrderDetail> order(@RequestHeader("X-User-Id") String userId, @PathVariable String id) {
        return ApiResponse.ok(orders.detail(userId, id), trace(), now());
    }

    @Operation(summary = "查询 /wallet")
    @GetMapping("/wallet")
    public ApiResponse<Map<String, String>> wallet(@RequestHeader("X-User-Id") String userId) {
        WalletView view = wallet.wallet(userId);
        Map<String, String> body = new LinkedHashMap<>();
        body.put("available", view.availableAmount());
        body.put("frozen", view.frozenAmount());
        body.put("monthEstimate", "0.00");
        return ApiResponse.ok(body, trace(), now());
    }

    @Operation(summary = "查询 /affiliate/stats")
    @GetMapping("/affiliate/stats")
    public ApiResponse<Map<String, Object>> affiliate(@RequestHeader("X-User-Id") String userId) {
        return ApiResponse.ok(accounts.affiliate(userId), trace(), now());
    }

    @Operation(summary = "提交 /memory/search")
    @PostMapping("/memory/search")
    public ApiResponse<List<Map<String, Object>>> memory() {
        return ApiResponse.ok(List.of(), trace(), now());
    }

    @Operation(summary = "查询 /credits")
    @GetMapping("/credits")
    public ApiResponse<Map<String, String>> credits(@RequestHeader("X-User-Id") String userId) {
        return ApiResponse.ok(credits.balance(userId), trace(), now());
    }

    private static String trace() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String now() {
        return OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString();
    }
/**
 * 解析输入。
 */

    private static OffsetDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(value);
    }
/**
 * 要查询的平台，必须是四个枚举之一。
 */

    public record PlatformRequest(String platform) {
    }
}
