package com.omino.shopai.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.api.ErrorCodes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
/**
 * 没有后台会话则 401。
 */

public class AdminSessionFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    public AdminSessionFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
/**
 * 登录回调、文档和健康检查不要求后台会话。
 */

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null
                || uri.contains("/actuator/health")
                || uri.contains("/v1/auth/")
                || uri.contains("/oauth2/")
                || uri.contains("/v3/api-docs")
                || uri.contains("/swagger-ui")
                || uri.contains("/webjars/");
    }
/**
 * 没有会话则 401。正文不出现 access_token 字样。
 */

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!hasSession(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ApiResponse.reject(
                    ErrorCodes.UNAUTHENTICATED,
                    "未登录",
                    request.getAttribute(ApiResponse.TRACE_ATTRIBUTE)));
            return;
        }
        filterChain.doFilter(request, response);
    }
/**
 * 只认 HttpOnly 的后台会话 Cookie。
 */

    private static boolean hasSession(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("sid_admin".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return true;
                }
            }
        }
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute("ssoSub") != null;
    }
}
