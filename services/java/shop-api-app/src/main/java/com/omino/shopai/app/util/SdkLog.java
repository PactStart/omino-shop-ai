package com.omino.shopai.app.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * 联盟 SDK 一次调用的请求和响应。密钥字段打成星号，正文过长则截断。
 */
public final class SdkLog {

    private static final Logger log = LoggerFactory.getLogger(SdkLog.class);
    private static final int LIMIT = 8000;
    private static final ObjectMapper JSON = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    private static final Pattern SECRET = Pattern.compile(
            "(\"(?:appSecret|app_secret|clientSecret|client_secret|secret|sign|session|accessToken|access_token)\"\\s*:\\s*\")([^\"]*)(\")",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRET_QUERY = Pattern.compile(
            "((?:app_secret|client_secret|secret|sign|session|access_token)=)[^&\\s,\"}]*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROMOTION = Pattern.compile(
            "(\"(?:pid|pId|relationId|relation_id|chanTag|openId)\"\\s*:\\s*\")([^\"]*)(\")",
            Pattern.CASE_INSENSITIVE);

    private static volatile UnionCallObserver meter = new UnionCallObserver() {
    };

    private SdkLog() {
    }

    /**
     * 换上出站计量。进程启动时绑一次，测试结束改回空实现。
     *
     * @param observer 配额、计数和限流识别；空则恢复为什么都不做
     */
    public static void bind(UnionCallObserver observer) {
        meter = observer == null ? new UnionCallObserver() {
        } : observer;
    }

    /**
     * 打出这一次官方调用。配额不够时不再出站。失败时带上异常类型，仍把原异常抛回去。
     *
     * @param platform 平台码
     * @param api 本系统给这个接口起的名字
     * @param request 官方请求，日志里会打码
     * @param call 真正的 SDK 调用
     * @param <T> 官方响应类型
     * @return 官方响应
     * @throws Exception 客户端抛出的原始异常
     */
    public static <T> T exchange(String platform, String api, Object request, Call<T> call) throws Exception {
        // 本地配额在出站前扣。没配配额时这一步直接返回。
        meter.before(platform, api);
        try {
            T response = call.get();
            log.info("sdk {} {} request={} response={}", platform, api, text(request), text(response));
            // 淘宝限流在响应体里，不抛异常，所以成功路径也要看一遍。
            meter.after(platform, api, response, null);
            return response;
        } catch (Exception ex) {
            log.warn("sdk {} {} request={} error={}: {}", platform, api, text(request), ex.getClass().getSimpleName(), text(ex.getMessage()));
            meter.after(platform, api, null, ex);
            throw ex;
        }
    }

    /**
     * 把请求或响应收成可落日志的文本。密钥和推广身份打成星号，过长则截断。
     *
     * @param value 请求、响应或异常消息
     * @return 打码后的文本
     */
    public static String text(Object value) {
        if (value == null) {
            return "null";
        }
        String raw;
        try {
            raw = value instanceof String text ? text : JSON.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            raw = String.valueOf(value);
        }
        raw = SECRET.matcher(raw).replaceAll("$1***$3");
        raw = SECRET_QUERY.matcher(raw).replaceAll("$1***");
        raw = PROMOTION.matcher(raw).replaceAll("$1***$3");
        if (raw.length() <= LIMIT) {
            return raw;
        }
        return raw.substring(0, LIMIT) + "…";
    }

    @FunctionalInterface
    public interface Call<T> {

        /**
         * 执行这一次官方调用。
         *
         * @return 官方响应
         * @throws Exception 客户端抛出的原始异常
         */
        T get() throws Exception;
    }
}
