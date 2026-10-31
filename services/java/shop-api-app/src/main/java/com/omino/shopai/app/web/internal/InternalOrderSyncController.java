package com.omino.shopai.app.web.internal;

import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.service.order.OrderSyncService;
import com.omino.shopai.enums.Platform;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 给管理端重试用的单平台增量。不是 Agent 工具。
 */
@RestController
@Tag(name = "内部订单同步")
@RequestMapping("/internal/v1/order-sync")
public class InternalOrderSyncController {

    private final OrderSyncService sync;

    /**
     * 注入已有的订单增量。
     *
     * @param sync 单平台同步
     */
    public InternalOrderSyncController(OrderSyncService sync) {
        this.sync = sync;
    }

    /**
     * 再拉一次这个平台的增量。失败时只记固定文案，不把异常原文返回。
     *
     * @param platform 平台码
     * @return 空
     */
    @Operation(summary = "再拉一次该平台的订单增量")
    @PostMapping("/{platform}")
    public ApiResponse<Void> sync(@PathVariable String platform) {
        Platform value;
        try {
            value = Platform.fromCode(platform);
        } catch (IllegalArgumentException ex) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "平台不正确");
        }
        if (value == null) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "平台不正确");
        }
        try {
            sync.sync(value.code());
        } catch (RuntimeException ex) {
            // 失败只留固定文案。水位停在失败前，响应里不带上游原文。
            sync.noteFailure(value.code());
        }
        return ApiResponse.ok(null);
    }
}
