package com.omino.shopai.admin.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.admin.web.dto.IssueRequestDto;
import com.omino.shopai.admin.service.godview.AdminGodViewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
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
    /**
     * 为指定用户签发短时上帝视角令牌。
     *
     * @param request 当前管理会话
     * @param body 要查看的用户
     * @return 令牌信封
     */
    @Operation(summary = "签发上帝视角令牌")
    @PostMapping("/tokens")
    public ApiResponse<Map<String, Object>> issue(HttpServletRequest request, @RequestBody IssueRequestDto body) {
        Object sub = request.getSession(false) == null ? null : request.getSession(false).getAttribute("ssoSub");
        String token = godView.issue(sub == null ? "admin" : sub.toString(), body.userId());
        return ApiResponse.ok(Map.of("token", token, "expiresIn", 3600));
    }
}
