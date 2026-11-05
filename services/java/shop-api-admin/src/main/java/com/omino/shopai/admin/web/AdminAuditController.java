package com.omino.shopai.admin.web;

import com.omino.shopai.admin.service.godview.AdminAuditQueryService;
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
 * 审计列表。
 */
@RestController
@Tag(name = "审计")
@RequestMapping("/v1/audit-logs")
public class AdminAuditController {

    private final AdminAuditQueryService audits;

    /**
     * 注入审计查询。
     *
     * @param audits 审计用例
     */
    public AdminAuditController(AdminAuditQueryService audits) {
        this.audits = audits;
    }

    /**
     * 按时间倒序分页读取审计。
     *
     * @param page 页码
     * @param size 页大小
     * @return 审计页
     */
    @Operation(summary = "分页查看审计")
    @GetMapping
    public ApiResponse<PageDto<Map<String, Object>>> list(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(audits.list(page, size));
    }
}
