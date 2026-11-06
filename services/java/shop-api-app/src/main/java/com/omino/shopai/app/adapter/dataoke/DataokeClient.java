package com.omino.shopai.app.adapter.dataoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.exception.PlatformCallException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * 大淘客开放平台。只取联想词和热搜词，密钥为空时不出站。
 */
@Component
public class DataokeClient {

    private static final String SUGGEST_URL = "https://openapi.dataoke.com/api/goods/search-suggestion";
    private static final String HOT_URL = "https://openapi.dataoke.com/api/category/get-top100";

    private final String appKey;
    private final String appSecret;
    private final HttpClient http;
    private final ObjectMapper json;

    /**
     * 用环境变量里的应用标识和密钥。任一为空则两个查询都返回空列表。
     *
     * @param appKey 大淘客 appKey
     * @param appSecret 大淘客 appSecret
     */
    public DataokeClient(
            @Value("${DATAOKE_APP_KEY:}") String appKey,
            @Value("${DATAOKE_APP_SECRET:}") String appSecret) {
        this.appKey = appKey == null ? "" : appKey;
        this.appSecret = appSecret == null ? "" : appSecret;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.json = new ObjectMapper();
    }

    /**
     * 按输入补全搜索词。
     *
     * @param keyword 用户已经输入的词
     * @return 补全词；未配置密钥时为空
     */
    public List<String> suggestions(String keyword) {
        if (!ready() || keyword == null || keyword.isBlank()) {
            return List.of();
        }
        TreeMap<String, String> params = base("v1.0.2");
        params.put("type", "2");
        params.put("keyWords", keyword.trim());
        JsonNode data = read(SUGGEST_URL, params).path("data");
        if (!data.isArray()) {
            return List.of();
        }
        List<String> words = new ArrayList<>();
        for (JsonNode row : data) {
            String word = row.path("kw").asText("");
            if (!word.isBlank()) {
                words.add(word);
            }
        }
        return List.copyOf(words);
    }

    /**
     * 读取热搜词。
     *
     * @return 热搜词；未配置密钥时为空
     */
    public List<String> hotWords() {
        if (!ready()) {
            return List.of();
        }
        JsonNode words = read(HOT_URL, base("v1.0.1")).path("data").path("hotWords");
        if (!words.isArray()) {
            return List.of();
        }
        List<String> found = new ArrayList<>();
        for (JsonNode row : words) {
            String word = row.asText("");
            if (!word.isBlank()) {
                found.add(word);
            }
        }
        return List.copyOf(found);
    }

    /**
     * 按参数名排序后做 MD5，结果大写。签名本身不放进待签名串。
     *
     * @param params 除 sign 以外的参数
     * @param secret 应用密钥
     * @return 大写 MD5
     */
    static String sign(TreeMap<String, String> params, String secret) {
        StringBuilder body = new StringBuilder();
        for (var entry : params.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(entry.getKey()).append('=').append(entry.getValue());
        }
        body.append("&key=").append(secret);
        return md5(body.toString()).toUpperCase();
    }

    private TreeMap<String, String> base(String version) {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("appKey", appKey);
        params.put("version", version);
        return params;
    }

    private boolean ready() {
        return !appKey.isBlank() && !appSecret.isBlank();
    }

    private JsonNode read(String url, TreeMap<String, String> params) {
        params.put("sign", sign(params, appSecret));
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url + "?" + query(params)))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new PlatformCallException();
            }
            JsonNode root = json.readTree(response.body());
            if (root.path("code").asInt(-1) != 0) {
                throw new PlatformCallException();
            }
            return root;
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    private static String query(TreeMap<String, String> params) {
        StringBuilder query = new StringBuilder();
        for (var entry : params.entrySet()) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return query.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String md5(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte piece : digest) {
                hex.append(Character.forDigit((piece >>> 4) & 0xf, 16));
                hex.append(Character.forDigit(piece & 0xf, 16));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }
}
