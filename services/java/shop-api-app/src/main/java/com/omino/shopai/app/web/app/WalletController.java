package com.omino.shopai.app.web.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.dto.WalletViewDto;
import com.omino.shopai.app.web.app.dto.ApplyRequestDto;
import com.omino.shopai.dto.PageDto;
import com.omino.shopai.app.service.wallet.WithdrawalService;
import com.omino.shopai.app.dto.WithdrawalViewDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    /**
     * 读取当前用户的返佣钱包余额。
     *
     * @param userId 当前用户
     * @return 钱包信封
     */
    @Operation(summary = "读取返佣钱包余额")
    @GetMapping("/wallet")
    public ApiResponse<WalletViewDto> wallet(@RequestAttribute("userId") String userId) {
        return ApiResponse.ok(withdrawals.wallet(userId));
    }
    /**
     * 用可提现余额发起一笔提现。
     *
     * @param userId 当前用户
     * @param request 金额、收款账号和渠道
     * @return 提现单信封
     */
    @Operation(summary = "申请提现")
    @PostMapping("/withdrawals")
    public ApiResponse<WithdrawalViewDto> apply(@RequestAttribute("userId") String userId, @RequestBody ApplyRequestDto request) {
        return ApiResponse.ok(
                withdrawals.apply(userId, request.amount(), request.payeeAccount(), request.channel()));
    }
    /**
     * 分页列出当前用户的提现记录。
     *
     * @param userId 当前用户
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @return 提现分页信封
     */
    @Operation(summary = "列出提现记录")
    @GetMapping("/withdrawals")
    public ApiResponse<PageDto<WithdrawalViewDto>> list(
            @RequestAttribute("userId") String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(withdrawals.list(userId, page, size));
    }
    /**
     * 读取当前用户的一笔提现。
     *
     * @param userId 当前用户
     * @param id 提现编号
     * @return 提现单信封
     */
    @Operation(summary = "读取一笔提现")
    @GetMapping("/withdrawals/{id}")
    public ApiResponse<WithdrawalViewDto> get(@RequestAttribute("userId") String userId, @PathVariable String id) {
        return ApiResponse.ok(withdrawals.get(userId, id));
    }
}
