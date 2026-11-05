package com.omino.shopai.admin.web;

import com.omino.shopai.admin.service.wallet.AdminWalletLedgerService;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.dto.PageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 返佣流水。
 */
@RestController
@Tag(name = "钱包流水")
@RequestMapping("/v1/wallet-ledger")
public class AdminWalletLedgerController {

    private final AdminWalletLedgerService ledger;

    /**
     * 注入流水查询。
     *
     * @param ledger 流水用例
     */
    public AdminWalletLedgerController(AdminWalletLedgerService ledger) {
        this.ledger = ledger;
    }

    /**
     * 分页查询返佣流水。
     *
     * @param userId 用户主键，可空
     * @param page 页码
     * @param size 页大小
     * @return 流水页
     */
    @Operation(summary = "分页查看返佣流水")
    @GetMapping
    public ApiResponse<PageDto<Map<String, Object>>> list(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(ledger.list(userId, page, size));
    }
}
