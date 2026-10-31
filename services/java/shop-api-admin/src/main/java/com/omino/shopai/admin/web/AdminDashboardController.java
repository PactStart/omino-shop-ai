package com.omino.shopai.admin.web;

import com.omino.shopai.admin.service.ops.AdminDashboardService;
import com.omino.shopai.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 工作台三个数字。
 */
@RestController
@Tag(name = "工作台")
@RequestMapping("/v1/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService dashboard;

    /**
     * 注入工作台计数。
     *
     * @param dashboard 计数用例
     */
    public AdminDashboardController(AdminDashboardService dashboard) {
        this.dashboard = dashboard;
    }

    /**
     * 读取同步失败、开单量和待审提现。没有数据时三个数都是 0。
     *
     * @return 三个整数
     */
    @Operation(summary = "查看工作台三个数字")
    @GetMapping
    public ApiResponse<Map<String, Long>> counts() {
        return ApiResponse.ok(dashboard.counts());
    }
}
