package com.omino.shopai.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 在 JSON 写出之前，把这一次请求的追踪号和上海时间写进信封。
 * 记录不能改字段，所以换一个新信封。回调原文、重定向和 SSE 不是信封，原样返回。
 */
@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    /**
     * 所有会进消息转换器的返回值都看一眼。不是信封的在写出前放过。
     *
     * @param returnType 控制器或异常处理方法的返回类型
     * @param converterType 选中的消息转换器
     * @return 始终检查正文
     */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    /**
     * 信封换成带本次追踪号和时间的新实例。其它正文不动。
     *
     * @param body 控制器或异常处理返回的正文
     * @param returnType 方法返回类型
     * @param selectedContentType 将要写出的媒体类型
     * @param selectedConverterType 选中的转换器
     * @param request 当前请求，追踪号在它的属性上
     * @param response 当前响应
     * @return 填好追踪号和时间的信封，或原来的正文
     */
    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (!(body instanceof ApiResponse<?> envelope)) {
            return body;
        }
        return new ApiResponse<>(
                envelope.code(),
                envelope.message(),
                envelope.data(),
                traceId(request),
                ApiResponse.now(),
                envelope.error());
    }

    /**
     * 取过滤器放上的追踪号。没有时现生成一个，避免单测直接调控制器时信封缺号。
     *
     * @param request 当前请求
     * @return 这一次请求的追踪号
     */
    private static String traceId(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servlet) {
            HttpServletRequest raw = servlet.getServletRequest();
            Object value = raw.getAttribute(ApiResponse.TRACE_ATTRIBUTE);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return ApiResponse.trace();
    }
}
