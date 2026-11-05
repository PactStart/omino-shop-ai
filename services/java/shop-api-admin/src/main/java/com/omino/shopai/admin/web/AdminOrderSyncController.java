package com.omino.shopai.admin.web;

import com.omino.shopai.admin.service.order.AdminOrderSyncService;
import com.omino.shopai.admin.web.dto.OrderSyncCursorDto;
import com.omino.shopai.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 订单同步游标和重试。
 */
@RestController
@Tag(name = "订单同步")
@RequestMapping("/v1/order-sync")
public class AdminOrderSyncController {

    private final AdminOrderSyncService sync;

    /**
     * 注入同步用例。
     *
     * @param sync 游标与重试
     */
    public AdminOrderSyncController(AdminOrderSyncService sync) {
        this.sync = sync;
    }

    /**
     * 读取四个平台的水位。
     *
     * @return 固定四行
     */
    @Operation(summary = "查看四个平台的同步水位")
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(sync.list());
    }

    /**
     * 改这个平台的下单水位和更新水位，并留下审计。
     *
     * @param request 当前管理会话
     * @param platform 平台码
     * @param body 两条水位，可空
     * @return 空
     */
    @Operation(summary = "设置一个平台的同步水位")
    @PatchMapping("/{platform}")
    public ApiResponse<Void> save(HttpServletRequest request, @PathVariable String platform, @RequestBody OrderSyncCursorDto body) {
        Object sub = request.getSession(false) == null ? null : request.getSession(false).getAttribute("ssoSub");
        OrderSyncCursorDto times = body == null ? new OrderSyncCursorDto(null, null) : body;
        sync.saveCursor(platform, times.lastSyncCreateTime(), times.lastSyncUpdateTime(), sub == null ? "admin" : sub.toString());
        return ApiResponse.ok(null);
    }

    /**
     * 再跑一次该平台的既有增量，并留下审计。
     *
     * @param request 当前管理会话
     * @param platform 平台码
     * @return 空
     */
    @Operation(summary = "重试一个平台的订单同步")
    @PostMapping("/{platform}/retry")
    public ApiResponse<Void> retry(HttpServletRequest request, @PathVariable String platform) {
        Object sub = request.getSession(false) == null ? null : request.getSession(false).getAttribute("ssoSub");
        sync.retry(platform, sub == null ? "admin" : sub.toString());
        return ApiResponse.ok(null);
    }
}
