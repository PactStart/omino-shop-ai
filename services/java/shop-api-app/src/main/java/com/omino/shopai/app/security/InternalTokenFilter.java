package com.omino.shopai.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.api.ErrorCodes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
/**
 * 内部工具接口的共享令牌。
 */

@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private final String expectedToken;
    private final ObjectMapper objectMapper;

    public InternalTokenFilter(
            @Value("${AGENT_INTERNAL_TOKEN:}") String expectedToken,
            ObjectMapper objectMapper) {
        this.expectedToken = expectedToken;
        this.objectMapper = objectMapper;
    }
/**
 * 只拦 /internal/v1，其它路径交给各自的过滤器。
 */

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.contains("/internal/v1/");
    }
/**
 * 比对内部令牌。不对则拒绝，避免外网直接打工具接口。
 */

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws java.io.IOException, jakarta.servlet.ServletException {
        String token = request.getHeader("X-Internal-Token");
        String userId = request.getHeader("X-User-Id");
        if (expectedToken.isBlank() || token == null || !expectedToken.equals(token) || userId == null || userId.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiResponse<Void> body = new ApiResponse<>(
                    ErrorCodes.UNAUTHENTICATED,
                    "未登录或 access_token 失效",
                    null,
                    UUID.randomUUID().toString().replace("-", ""),
                    OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString());
            objectMapper.writeValue(response.getWriter(), body);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
