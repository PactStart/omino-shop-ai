package com.omino.shopai.app.web.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.service.wallet.WalletView;
import com.omino.shopai.app.service.wallet.WithdrawalPage;
import com.omino.shopai.app.service.wallet.WithdrawalService;
import com.omino.shopai.app.service.wallet.WithdrawalView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
/**
 * WalletController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "WalletController")
@RequestMapping("/v1")
public class WalletController {

    private final WithdrawalService withdrawals;

    public WalletController(WithdrawalService withdrawals) {
        this.withdrawals = withdrawals;
    }

    @Operation(summary = "查询 /wallet")
    @GetMapping("/wallet")
    public ApiResponse<WalletView> wallet(@RequestAttribute("userId") String userId) {
        return ApiResponse.ok(withdrawals.wallet(userId), traceId(), now());
    }

    @Operation(summary = "提交 /withdrawals")
    @PostMapping("/withdrawals")
    public ApiResponse<WithdrawalView> apply(@RequestAttribute("userId") String userId, @RequestBody ApplyRequest request) {
        return ApiResponse.ok(
                withdrawals.apply(userId, request.amount(), request.payeeAccount(), request.channel()),
                traceId(),
                now());
    }

    @Operation(summary = "查询 /withdrawals")
    @GetMapping("/withdrawals")
    public ApiResponse<WithdrawalPage> list(
            @RequestAttribute("userId") String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(withdrawals.list(userId, page, size), traceId(), now());
    }

    @Operation(summary = "查询 /withdrawals/{id}")
    @GetMapping("/withdrawals/{id}")
    public ApiResponse<WithdrawalView> get(@RequestAttribute("userId") String userId, @PathVariable String id) {
        return ApiResponse.ok(withdrawals.get(userId, id), traceId(), now());
    }

    private static String traceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String now() {
        return OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString();
    }
/**
 * 提现申请。渠道在服务里收成枚举。
 */

    public record ApplyRequest(String amount, String payeeAccount, String channel) {
    }
}
