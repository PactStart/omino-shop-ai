package com.omino.shopai.admin.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.admin.service.godview.AdminGodViewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
/**
 * AdminGodViewController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "AdminGodViewController")
@RequestMapping("/v1/god-view")
public class AdminGodViewController {

    private final AdminGodViewService godView;

    public AdminGodViewController(AdminGodViewService godView) {
        this.godView = godView;
    }

    @Operation(summary = "提交 /tokens")
    @PostMapping("/tokens")
    public ApiResponse<Map<String, Object>> issue(HttpServletRequest request, @RequestBody IssueRequest body) {
        Object sub = request.getSession(false) == null ? null : request.getSession(false).getAttribute("ssoSub");
        String token = godView.issue(sub == null ? "admin" : sub.toString(), body.userId());
        return ApiResponse.ok(Map.of("token", token, "expiresIn", 3600), trace(), now());
    }

    private static String trace() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String now() {
        return OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString();
    }
/**
 * 上帝视角要签发的用户。
 */

    public record IssueRequest(String userId) {
    }
}
