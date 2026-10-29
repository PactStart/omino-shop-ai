package com.omino.shopai.app.service.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
/**
 * 按内网地址把一轮对话转给 Agent。
 */

@Service
@Profile({"test", "prod"})
public class HttpAgentGateway implements AgentGateway {

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String baseUrl;
    private final String token;

    public HttpAgentGateway(
            @Value("${AGENT_BASE_URL:http://127.0.0.1:8090}") String baseUrl,
            @Value("${AGENT_INTERNAL_TOKEN:}") String token) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
    }

    @Override
    public void stream(ChatRun run, AgentSink sink) {
        stream(run, RunContext.passthrough(), sink);
    }
/**
 * 把事件按流推送。
 */

    @Override
    public void stream(ChatRun run, RunContext context, AgentSink sink) {
        String body = """
                {"traceId":"%s","userId":"%s","conversationId":"%s","clientMsgId":"%s","inputType":"text","text":"%s","installedPlatforms":[],"authorizedPlatforms":[],"prompts":%s,"llm":%s}
                """.formatted(
                json(run.runId()),
                json(run.userId()),
                json(run.conversationId()),
                json(run.clientMsgId()),
                json(run.text()),
                prompts(context),
                llm(context)).replace("\n", "");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/internal/v1/graph/invoke"))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "text/event-stream")
                .header("Content-Type", "application/json")
                .header("X-Internal-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new AgentUnavailableException();
            }
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                readSse(reader, sink);
            }
        } catch (AgentAborted ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AgentUnavailableException();
        }
    }
/**
 * 把 Java 侧上下文收成 Agent 能读的 JSON。
 */

    private static String json(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
/**
 * 带上八个提示词。缺了就不要发请求。
 */

    private static String prompts(RunContext context) {
        if (context == null || context.prompts() == null || context.prompts().isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (var entry : context.prompts().entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(json(entry.getKey())).append("\":\"").append(json(entry.getValue())).append('"');
        }
        return builder.append('}').toString();
    }
/**
 * 有分流结果才带上模型。明文 Key 只出现在这次请求体里。
 */

    private static String llm(RunContext context) {
        if (context == null || context.llm() == null) {
            return "null";
        }
        RunContext.LlmChoice choice = context.llm();
        return "{\"id\":\"" + json(choice.id())
                + "\",\"baseUrl\":\"" + json(choice.baseUrl())
                + "\",\"modelName\":\"" + json(choice.modelName())
                + "\",\"apiKey\":\"" + json(choice.apiKey())
                + "\",\"timeoutMs\":" + choice.timeoutMs() + "}";
    }
/**
 * 按事件把 Agent 的流交给回调。连接中断不当成用户取消。
 */

    private static void readSse(java.io.BufferedReader reader, AgentSink sink) throws java.io.IOException {
        String event = null;
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
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
            }
        }
    }
}
