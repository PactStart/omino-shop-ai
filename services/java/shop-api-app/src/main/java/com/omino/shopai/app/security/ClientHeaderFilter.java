package com.omino.shopai.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.api.ErrorCodes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
/**
 * 要求渠道和端类型。文档路径放行。
 */

public class ClientHeaderFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    public ClientHeaderFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
/**
 * 回调、文档和健康检查不要求客户端头。
 */

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null
                || uri.contains("/internal/")
                || uri.contains("/v1/auth/sms/")
                || uri.contains("/v1/pay/")
                || uri.contains("/v1/me/authorizations/taobao/callback")
                || uri.contains("/actuator/health")
                || uri.contains("/v3/api-docs")
                || uri.contains("/swagger-ui")
                || uri.contains("/webjars/");
    }
/**
 * 缺渠道或端类型则拒绝，避免无法归因的请求进入业务。
 */

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (blank(request.getHeader("X-Client-Type"))
                || blank(request.getHeader("X-App-Channel"))
                || blank(request.getHeader("X-App-Version-Code"))) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ApiResponse.reject(
                    ErrorCodes.VALIDATION,
                    "缺少客户端头",
                    request.getAttribute(ApiResponse.TRACE_ATTRIBUTE)));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
