package com.omino.shopai.admin.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
/**
 * AdminAuthController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "AdminAuthController")
@RequestMapping("/v1/auth")
public class AdminAuthController {

    private final String issuer;

    public AdminAuthController(@Value("${SSO_ISSUER:http://127.0.0.1:8080}") String issuer) {
        this.issuer = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
    }
    /**
     * 把浏览器重定向到统一登录。
     *
     * @param request 当前请求
     * @param response 用于写出重定向
     * @return 无，由重定向完成
     */
    @Operation(summary = "跳转到统一登录")
    @GetMapping("/login")
    public void login(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect(request.getContextPath() + "/oauth2/authorization/omino");
    }
    /**
     * 用授权码换会话并回到后台。
     *
     * @param request 当前请求，带授权码
     * @param response 用于写会话并重定向
     * @return 无，由重定向完成
     */
    @Operation(summary = "完成登录回调")
    @GetMapping("/callback")
    public void callback(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect(request.getContextPath() + "/v1/users");
    }
    /**
     * 清掉后台会话。
     *
     * @param request 当前请求
     * @param response 用于清会话
     * @return 无
     */
    @Operation(summary = "退出管理后台")
    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.sendRedirect(issuer + "/logout");
    }
}
