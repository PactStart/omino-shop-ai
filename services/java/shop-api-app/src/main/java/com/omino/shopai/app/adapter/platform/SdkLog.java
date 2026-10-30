package com.omino.shopai.app.adapter.platform;

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

    private SdkLog() {
    }

    /**
     * 打出这一次官方调用。失败时带上异常类型，仍把原异常抛回去。
     */
    static <T> T exchange(String platform, String api, Object request, Call<T> call) throws Exception {
        try {
            T response = call.get();
            log.info("sdk {} {} request={} response={}", platform, api, text(request), text(response));
            return response;
        } catch (Exception ex) {
            log.warn("sdk {} {} request={} error={}: {}", platform, api, text(request), ex.getClass().getSimpleName(), text(ex.getMessage()));
            throw ex;
        }
    }

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
        if (raw.length() <= LIMIT) {
            return raw;
        }
        return raw.substring(0, LIMIT) + "…";
    }

    @FunctionalInterface
    interface Call<T> {
        T get() throws Exception;
    }
}
