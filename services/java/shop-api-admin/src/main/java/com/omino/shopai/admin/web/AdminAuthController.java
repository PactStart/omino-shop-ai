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

    @Operation(summary = "查询 /login")
    @GetMapping("/login")
    public void login(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect(request.getContextPath() + "/oauth2/authorization/omino");
    }

    @Operation(summary = "查询 /callback")
    @GetMapping("/callback")
    public void callback(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect(request.getContextPath() + "/v1/users");
    }
/**
 * 退出登录。
 */

    @Operation(summary = "提交 /logout")
    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.sendRedirect(issuer + "/logout");
    }
}
