package com.omino.shopai.app.web.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.web.internal.dto.PlatformRequestDto;
import com.omino.shopai.app.service.auth.InternalAccountService;
import com.omino.shopai.app.service.credit.CreditBillingImpl;
import com.omino.shopai.app.dto.OrderDetailDto;
import com.omino.shopai.app.dto.OrderViewDto;
import com.omino.shopai.app.dto.PageDto;
import com.omino.shopai.app.service.order.OrderQueryService;
import com.omino.shopai.app.dto.WalletViewDto;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final CreditBillingImpl credits;
/**
 * Agent 工具的账号入口。调用方必须带当前用户。
 */

    public InternalAccountController(
            InternalAccountService accounts,
            OrderQueryService orders,
            WithdrawalService wallet,
            CreditBillingImpl credits) {
        this.accounts = accounts;
        this.orders = orders;
        this.wallet = wallet;
        this.credits = credits;
    }
    /**
     * 读取当前对话用户的资料。
     *
     * @param userId 当前对话用户
     * @return 资料信封
     */
    @Operation(summary = "读取当前用户资料")
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@RequestHeader("X-User-Id") String userId) {
        return ApiResponse.ok(accounts.me(userId));
    }
    /**
     * 列出当前用户在四个平台的授权状态。
     *
     * @param userId 当前对话用户
     * @return 授权列表信封
     */
    @Operation(summary = "列出联盟授权状态")
    @GetMapping("/me/authorizations")
    public ApiResponse<List<Map<String, Object>>> authorizations(@RequestHeader("X-User-Id") String userId) {
        return ApiResponse.ok(accounts.authorizations(userId));
    }
    /**
     * 开始一个平台的联盟授权。
     *
     * @param request 平台码
     * @return 授权引导信封
     */
    @Operation(summary = "开始联盟授权")
    @PostMapping("/me/authorizations/start")
    public ApiResponse<Map<String, String>> start(@RequestBody PlatformRequestDto request) {
        return ApiResponse.ok(accounts.startAuthorization(request.platform()));
    }
    /**
     * 取消当前用户在一个平台的联盟授权。
     *
     * @param userId 当前对话用户
     * @param request 平台码
     * @return 授权结果信封
     */
    @Operation(summary = "取消联盟授权")
    @PostMapping("/me/authorizations/revoke")
    public ApiResponse<Map<String, String>> revoke(@RequestHeader("X-User-Id") String userId, @RequestBody PlatformRequestDto request) {
        return ApiResponse.ok(accounts.revokeAuthorization(userId, request.platform()));
    }
    /**
     * 按条件列出当前用户的电商订单。
     *
     * @param userId 当前对话用户
     * @param platform 平台码，可空
     * @param orderStatus 订单状态，可空
     * @param settleStatus 结算状态，可空
     * @param page 页码
     * @param size 每页条数
     * @return 订单分页信封
     */
    @Operation(summary = "列出当前用户的订单")
    @GetMapping("/orders")
    public ApiResponse<PageDto<OrderViewDto>> orders(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "orderStatus", required = false) String orderStatus,
            @RequestParam(value = "settleStatus", required = false) String settleStatus,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(orders.list(userId, platform, orderStatus, settleStatus, page, size));
    }
    /**
     * 统计当前用户一段时间的订单。
     *
     * @param userId 当前对话用户
     * @param from 开始时间，可空
     * @param to 结束时间，可空
     * @return 统计信封
     */
    @Operation(summary = "统计一段时间的订单")
    @GetMapping("/orders/stats")
    public ApiResponse<Map<String, Object>> stats(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        return ApiResponse.ok(orders.stats(userId, parse(from), parse(to)));
    }
    /**
     * 读取当前用户的一笔电商订单。
     *
     * @param userId 当前对话用户
     * @param id 订单编号
     * @return 订单详情信封
     */
    @Operation(summary = "读取一笔订单")
    @GetMapping("/orders/{id}")
    public ApiResponse<OrderDetailDto> order(@RequestHeader("X-User-Id") String userId, @PathVariable String id) {
        return ApiResponse.ok(orders.detail(userId, id));
    }
    /**
     * 读取当前用户的返佣钱包。
     *
     * @param userId 当前对话用户
     * @return 钱包信封
     */
    @Operation(summary = "读取返佣钱包")
    @GetMapping("/wallet")
    public ApiResponse<Map<String, String>> wallet(@RequestHeader("X-User-Id") String userId) {
        WalletViewDto view = wallet.wallet(userId);
        Map<String, String> body = new LinkedHashMap<>();
        body.put("available", view.availableAmount());
        body.put("frozen", view.frozenAmount());
        body.put("monthEstimate", "0.00");
        return ApiResponse.ok(body);
    }
    /**
     * 读取当前用户的邀请统计。
     *
     * @param userId 当前对话用户
     * @return 邀请统计信封
     */
    @Operation(summary = "读取邀请统计")
    @GetMapping("/affiliate/stats")
    public ApiResponse<Map<String, Object>> affiliate(@RequestHeader("X-User-Id") String userId) {
        return ApiResponse.ok(accounts.affiliate(userId));
    }
    /**
     * 检索当前用户的记忆。本阶段返回空列表。
     *
     * @return 记忆列表信封
     */
    @Operation(summary = "检索用户记忆")
    @PostMapping("/memory/search")
    public ApiResponse<List<Map<String, Object>>> memory() {
        return ApiResponse.ok(List.of());
    }
    /**
     * 读取当前用户的积分余额。
     *
     * @param userId 当前对话用户
     * @return 余额信封
     */
    @Operation(summary = "读取积分余额")
    @GetMapping("/credits")
    public ApiResponse<Map<String, String>> credits(@RequestHeader("X-User-Id") String userId) {
        return ApiResponse.ok(credits.balance(userId));
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
}
