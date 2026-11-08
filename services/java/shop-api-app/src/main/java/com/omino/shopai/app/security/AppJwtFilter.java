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
import java.util.function.Predicate;
/**
 * 校验 APP 访问令牌。文档路径放行。
 */

public class AppJwtFilter extends OncePerRequestFilter {

    private final AppJwtIssuer tokens;
    private final ObjectMapper objectMapper;
    private final Predicate<String> active;

    /**
     * 注入签发器。测试里不查库，默认把任何已验签用户当作仍有效。
     *
     * @param tokens 访问令牌
     * @param objectMapper 写 401 信封
     */
    public AppJwtFilter(AppJwtIssuer tokens, ObjectMapper objectMapper) {
        this(tokens, objectMapper, userId -> true);
    }

    /**
     * 注入签发器和账号是否仍可登录的判断。注销后的用户在这里被挡下。
     *
     * @param tokens 访问令牌
     * @param objectMapper 写 401 信封
     * @param active 用户主键仍对应未注销账号时为真
     */
    public AppJwtFilter(AppJwtIssuer tokens, ObjectMapper objectMapper, Predicate<String> active) {
        this.tokens = tokens;
        this.objectMapper = objectMapper;
        this.active = active;
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
                || uri.contains("/v1/me/authorizations/taobao/callback")
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
        String token = header.substring("Bearer ".length()).trim();
        Optional<String> userId = tokens.subject(token);
        if (userId.isEmpty() || !active.test(userId.get())) {
            reject(request, response);
            return;
        }
        request.setAttribute("userId", userId.get());
        request.setAttribute("godView", tokens.godView(token));
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
