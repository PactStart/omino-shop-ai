package com.omino.shopai.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 两个产出物共用的访问日志。成功时只记方法和结果。出错时补上查询字符串和请求体，不记请求头。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpAccessLogFilter.class);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter LOG_TS = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
    private static final int PEEK_BYTES = 8192;
    private static final int BODY_CHARS = 2048;
    private static final Pattern CODE = Pattern.compile("\"code\"\\s*:\\s*(-?\\d+)");
    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern TS = Pattern.compile("\"ts\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern SECRET_JSON = Pattern.compile(
            "(\"(?:password|secret|token|authorization|captcha|code)\"\\s*:\\s*\")([^\"]*)(\")",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRET_QUERY = Pattern.compile(
            "((?:^|&)(?:password|secret|token|authorization|captcha|code)=)[^&]*",
            Pattern.CASE_INSENSITIVE);

    /**
     * 健康检查很密，不写访问日志。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.contains("/actuator/");
    }

    /**
     * 包住响应，取出信封里的 code 和 message。流式响应不缓存正文。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        String traceId = ApiResponse.trace();
        request.setAttribute(ApiResponse.TRACE_ATTRIBUTE, traceId);
        ContentCachingRequestWrapper cached = new ContentCachingRequestWrapper(request, PEEK_BYTES);
        if (stream(request)) {
            try {
                filterChain.doFilter(cached, response);
            } finally {
                write(cached, response, null, started);
            }
            return;
        }
        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(cached, wrapped);
        } finally {
            write(cached, wrapped, wrapped.getContentAsByteArray(), started);
            wrapped.copyBodyToResponse();
        }
    }

    private static boolean stream(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String accept = request.getHeader("Accept");
        return (uri != null && uri.contains("/events"))
                || (accept != null && accept.contains("text/event-stream"));
    }

    /**
     * 打一行访问日志。追踪号用进 Controller 之前放上的那个，时间打成 yyyyMMdd HH:mm:ss。
     *
     * @param request 已缓存正文的请求
     * @param response 当前响应
     * @param body 响应正文，流式响应为空
     * @param started 请求开始的纳秒时间
     */
    private static void write(ContentCachingRequestWrapper request, HttpServletResponse response, byte[] body, long started) {
        String code = "-";
        String message = "-";
        if (body != null && body.length > 0) {
            String prefix = text(body);
            Matcher codeMatch = CODE.matcher(prefix);
            if (codeMatch.find()) {
                code = codeMatch.group(1);
            }
            Matcher messageMatch = MESSAGE.matcher(prefix);
            if (messageMatch.find()) {
                message = messageMatch.group(1);
            }
        }
        long millis = (System.nanoTime() - started) / 1_000_000L;
        String traceId = traceOf(request);
        String ts = tsOf(body);
        if (failed(response.getStatus(), code)) {
            log.info("http {} {} traceId={} ts={} status={} code={} message={} {}ms query={} body={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    traceId,
                    ts,
                    response.getStatus(),
                    code,
                    message,
                    millis,
                    query(request),
                    requestBody(request));
            return;
        }
        log.info("http {} {} traceId={} ts={} status={} code={} message={} {}ms",
                request.getMethod(),
                request.getRequestURI(),
                traceId,
                ts,
                response.getStatus(),
                code,
                message,
                millis);
    }

    /**
     * 响应正文里已有信封时间就换算成日志格式，否则用打日志这一刻的上海时间。
     *
     * @param body 响应正文，可空
     * @return `yyyyMMdd HH:mm:ss`
     */
    private static String tsOf(byte[] body) {
        if (body != null && body.length > 0) {
            Matcher match = TS.matcher(text(body));
            if (match.find() && !match.group(1).isBlank()) {
                return formatTs(match.group(1));
            }
        }
        return OffsetDateTime.now(SHANGHAI).format(LOG_TS);
    }

    /**
     * 把信封里的时间换成上海时间的日志格式。解析不了就原样留下。
     *
     * @param raw 信封里的 ts
     * @return `yyyyMMdd HH:mm:ss`，或原来的字符串
     */
    private static String formatTs(String raw) {
        try {
            return OffsetDateTime.parse(raw).atZoneSameInstant(SHANGHAI).format(LOG_TS);
        } catch (DateTimeParseException ex) {
            return raw;
        }
    }

    /**
     * 读进 Controller 之前放上的追踪号。
     *
     * @param request 当前请求
     * @return 追踪号，没有时用横线
     */
    private static String traceOf(HttpServletRequest request) {
        Object value = request.getAttribute(ApiResponse.TRACE_ATTRIBUTE);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return "-";
    }

    /** HTTP 4xx/5xx，或信封 code 不是 0，都算这次请求失败。 */
    private static boolean failed(int status, String code) {
        return status >= 400 || (!"-".equals(code) && !"0".equals(code));
    }

    private static String query(HttpServletRequest request) {
        String raw = request.getQueryString();
        if (raw == null || raw.isBlank()) {
            return "-";
        }
        return clip(SECRET_QUERY.matcher(raw).replaceAll("$1***"));
    }

    private static String requestBody(ContentCachingRequestWrapper request) {
        byte[] cached = request.getContentAsByteArray();
        if (cached.length == 0) {
            return "-";
        }
        String raw = text(cached);
        return clip(SECRET_JSON.matcher(raw).replaceAll("$1***$3"));
    }

    /**
     * 信封和请求体都是 UTF-8。Servlet 没标明编码时默认 Latin-1，按那个解会把中文拆开。
     */
    private static String text(byte[] body) {
        int length = Math.min(body.length, PEEK_BYTES);
        return new String(body, 0, length, StandardCharsets.UTF_8);
    }

    private static String clip(String raw) {
        if (raw.length() <= BODY_CHARS) {
            return raw;
        }
        return raw.substring(0, BODY_CHARS) + "…";
    }

}
