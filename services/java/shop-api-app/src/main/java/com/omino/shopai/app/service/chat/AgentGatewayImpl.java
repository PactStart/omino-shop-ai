package com.omino.shopai.app.service.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.entity.PlatformAuthorization;
import com.omino.shopai.enums.AuthStatus;
import com.omino.shopai.exception.AgentUnavailableException;
import com.omino.shopai.mapper.PlatformAuthorizationMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * 按内网地址把一轮对话转给 Agent。local、test、prod 都装配。
 */
@Service
@Profile({"local", "test", "prod"})
public class AgentGatewayImpl implements IAgentGateway {

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String baseUrl;
    private final String token;
    private final Duration streamTimeout;
    private final ObjectMapper json;
    private final PlatformAuthorizationMapper authorizations;
    private final IChatEventService events;

    /**
     * 注入内网地址、整段流的截止时间和已授权平台。
     *
     * @param baseUrl 助手根地址
     * @param token 内网令牌
     * @param timeoutSeconds 整段流允许多少秒，不再把请求限制成 5 秒
     * @param json 请求体序列化
     * @param authorizations 联盟授权
     * @param events 取消标记
     */
    public AgentGatewayImpl(
            @Value("${AGENT_BASE_URL:http://127.0.0.1:8190}") String baseUrl,
            @Value("${AGENT_INTERNAL_TOKEN:}") String token,
            @Value("${chat.run-timeout-seconds:120}") long timeoutSeconds,
            ObjectMapper json,
            PlatformAuthorizationMapper authorizations,
            IChatEventService events) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
        this.streamTimeout = Duration.ofSeconds(timeoutSeconds);
        this.json = json;
        this.authorizations = authorizations;
        this.events = events;
    }

    /**
     * 没有上下文时仍按文本转发。
     *
     * @param run 这一轮
     * @param sink 事件回调
     */
    @Override
    public void stream(ChatRun run, AgentSink sink) {
        stream(run, RunContext.passthrough(), sink);
    }

    /**
     * 把事件按流推送。异常只保留固定说明，不打印调用正文。
     *
     * @param run 这一轮
     * @param context 提示词和模型
     * @param sink 事件回调
     */
    @Override
    public void stream(ChatRun run, RunContext context, AgentSink sink) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/internal/v1/graph/invoke"))
                .timeout(streamTimeout)
                .header("Accept", "text/event-stream")
                .header("Content-Type", "application/json")
                .header("X-Internal-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(body(run, context)))
                .build();
        boolean sawHeaders = false;
        try {
            HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            sawHeaders = true;
            if (response.statusCode() >= 400) {
                throw new AgentUnavailableException(false);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                boolean stopped = readSse(reader, sink, () -> events.cancelled(run.runId()), response.body()::close);
                if (stopped && !events.terminal(run.runId())) {
                    events.append(run.runId(), "cancelled", "{\"runId\":\"" + run.runId() + "\"}");
                }
                if (stopped) {
                    throw new AgentAborted();
                }
            }
        } catch (AgentAborted ex) {
            throw ex;
        } catch (AgentUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw unavailable(ex, sawHeaders);
        }
    }

    /**
     * 组装助手请求。模型字段名是 model。
     *
     * @param run 这一轮
     * @param context 提示词和模型
     * @return JSON 正文
     */
    private String body(ChatRun run, RunContext context) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("traceId", run.runId());
        body.put("userId", run.userId());
        body.put("conversationId", run.conversationId());
        body.put("clientMsgId", run.clientMsgId());
        body.put("inputType", run.inputType());
        body.put("text", run.text());
        body.put("sourceUrl", run.sourceUrl());
        body.put("imageObjectKey", run.imageObjectKey());
        body.put("hitl", run.hitl());
        body.put("installedPlatforms", run.installedPlatforms());
        body.put("authorizedPlatforms", authorized(run.userId()));
        body.put("prompts", context == null || context.prompts() == null ? Map.of() : context.prompts());
        body.put("llm", llm(context));
        try {
            return json.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new AgentUnavailableException(false);
        }
    }

    /**
     * 读出该用户已经授权的平台码。
     *
     * @param userId 用户主键
     * @return 平台码
     */
    private List<String> authorized(String userId) {
        return authorizations.selectList(new LambdaQueryWrapper<PlatformAuthorization>()
                        .eq(PlatformAuthorization::getUserId, userId)
                        .eq(PlatformAuthorization::getStatus, AuthStatus.AUTHORIZED))
                .stream()
                .map(row -> row.getPlatform().code())
                .toList();
    }

    /**
     * 有分流结果才带上模型。字段名用 model，明文 Key 只出现在这次请求体里。
     *
     * @param context 提示词和模型
     * @return 模型对象；没有则为空
     */
    private static Map<String, Object> llm(RunContext context) {
        if (context == null || context.llm() == null) {
            return null;
        }
        RunContext.LlmChoice choice = context.llm();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", choice.id());
        body.put("baseUrl", choice.baseUrl());
        body.put("model", choice.modelName());
        body.put("apiKey", choice.apiKey());
        body.put("timeoutMs", choice.timeoutMs());
        return body;
    }

    /**
     * 按事件把助手的流交给回调。取消标记会关掉流。
     *
     * @param reader 响应正文
     * @param sink 事件回调
     * @param cancelled 是否已取消
     * @param close 关掉响应流
     * @return 因取消而停下时为真
     * @throws IOException 读取失败
     */
    public static boolean readSse(BufferedReader reader, AgentSink sink, BooleanSupplier cancelled, IORunnable close) throws IOException {
        String event = null;
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (cancelled.getAsBoolean()) {
                close.run();
                return true;
            }
            if (line.startsWith("event:")) {
                event = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring("data:".length()).trim());
            } else if (line.isBlank() && event != null) {
                sink.event(event, data.toString());
                event = null;
                data.setLength(0);
                if (cancelled.getAsBoolean()) {
                    close.run();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 连接被拒绝或响应头之前超时才算还没接上。
     *
     * @param ex 调用失败
     * @param sawHeaders 是否已经拿到响应头
     * @return 给工人区分问候和错误的异常
     */
    private static AgentUnavailableException unavailable(Exception ex, boolean sawHeaders) {
        if (!sawHeaders && (ex instanceof ConnectException || ex instanceof HttpConnectTimeoutException || ex instanceof HttpTimeoutException)) {
            return new AgentUnavailableException(true);
        }
        return new AgentUnavailableException(false);
    }

    /**
     * 关掉响应流时允许抛出 IO 异常。
     */
    @FunctionalInterface
    public interface IORunnable {
        /**
         * 关掉流。
         *
         * @throws IOException 关闭失败
         */
        void run() throws IOException;
    }
}
