package com.omino.shopai.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.service.auth.AppJwtIssuer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
/**
 * 校验 APP 访问令牌。文档路径放行。
 */

public class AppJwtFilter extends OncePerRequestFilter {

    private final AppJwtIssuer tokens;
    private final ObjectMapper objectMapper;
/**
 * 注入签发器。无令牌时写 401 信封。
 */

    public AppJwtFilter(AppJwtIssuer tokens, ObjectMapper objectMapper) {
        this.tokens = tokens;
        this.objectMapper = objectMapper;
    }
/**
 * 登录、回调、文档和健康检查不要求 access token。
 */

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null
                || uri.contains("/internal/")
                || uri.contains("/v1/auth/sms/")
                || uri.contains("/v1/pay/")
                || uri.contains("/v1/invite/lookup")
                || uri.contains("/v1/app/check-update")
                || uri.contains("/v1/legal")
                || uri.contains("/actuator/health")
                || uri.contains("/v3/api-docs")
                || uri.contains("/swagger-ui")
                || uri.contains("/webjars/");
    }
/**
 * 验 Bearer。失败写 40100，成功把用户放进上下文。
 */

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            reject(request, response);
            return;
        }
        Optional<String> userId = tokens.subject(header.substring("Bearer ".length()).trim());
        if (userId.isEmpty()) {
            reject(request, response);
            return;
        }
        request.setAttribute("userId", userId.get());
        filterChain.doFilter(request, response);
    }
    /**
     * 未登录时直接写 401 信封。追踪号用这一次请求上已有的。
     *
     * @param request 当前请求
     * @param response 当前响应
     */
    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.reject(
                ErrorCodes.UNAUTHENTICATED,
                "未登录或 access_token 失效",
                request.getAttribute(ApiResponse.TRACE_ATTRIBUTE)));
    }
}
