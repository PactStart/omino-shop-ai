package com.omino.shopai.admin.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
/**
 * AdminAuthController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "AdminAuthController")
@RequestMapping("/v1/auth")
public class AdminAuthController {

    private final String webOrigin;
    private final String ssoLoginUrl;

    /**
     * 注入后台页面源和门户登录页。
     *
     * @param webOrigin 管理后台页面源，登录成功后回到这里
     * @param ssoLoginUrl 门户登录页，登出时用来定位「我的应用」，可空
     */
    public AdminAuthController(
            @Value("${shop.admin-web-origin:http://127.0.0.1:5183}") String webOrigin,
            @Value("${shop.sso-login-url:}") String ssoLoginUrl) {
        this.webOrigin = webOrigin.endsWith("/") ? webOrigin.substring(0, webOrigin.length() - 1) : webOrigin;
        this.ssoLoginUrl = ssoLoginUrl == null ? "" : ssoLoginUrl.trim();
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
        // 会话已经在授权码交换里建立。这里只回到前端源，地址里不放访问令牌。
        response.sendRedirect(webOrigin + "/");
    }
    /**
     * 作废本应用会话和 Cookie，再打开门户「我的应用」。不结束统一身份会话。
     *
     * @param request 当前请求
     * @param response 用于清 Cookie 并跳转
     * @return 无，由重定向完成
     */
    @Operation(summary = "退出管理后台")
    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 先丢掉登录主体，避免过滤器在请求结束时把旧会话写回去。
        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        SecurityContextHolder.clearContext();
        String path = request.getContextPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        response.setHeader("Set-Cookie", "sid_admin=; Path=" + path + "; Max-Age=0; HttpOnly; SameSite=Lax");
        String apps = appsPage(ssoLoginUrl);
        if (apps.isEmpty()) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        response.sendRedirect(apps);
    }

    /**
     * 用门户登录页的源拼出「我的应用」。
     *
     * @param loginUrl 门户登录页，如 http://localhost:5173/login
     * @return 该源下的 /account/apps；登录页无法解析时为空
     */
    static String appsPage(String loginUrl) {
        if (loginUrl == null || loginUrl.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(loginUrl.trim());
            if (uri.getScheme() == null || uri.getRawAuthority() == null || uri.getRawAuthority().isBlank()) {
                return "";
            }
            return uri.getScheme() + "://" + uri.getRawAuthority() + "/account/apps";
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }
}
