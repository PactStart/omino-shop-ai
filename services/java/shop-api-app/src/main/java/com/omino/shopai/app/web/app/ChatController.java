package com.omino.shopai.app.web.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.service.chat.ChatCancelService;
import com.omino.shopai.app.service.chat.ChatEventPump;
import com.omino.shopai.app.service.chat.ChatEvents;
import com.omino.shopai.app.service.chat.ChatTurnService;
import com.omino.shopai.app.service.chat.TurnAccepted;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * ChatController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "ChatController")
@RequestMapping("/v1/chat")
public class ChatController {

    private final ChatTurnService turns;
    private final ChatEventPump pump;
    private final ChatCancelService cancels;
/**
 * 开 run、泵 SSE、取消。断开连接不在这里取消生成。
 */

    public ChatController(ChatTurnService turns, ChatEventPump pump, ChatCancelService cancels) {
        this.turns = turns;
        this.pump = pump;
        this.cancels = cancels;
    }
/**
 * 受理一轮对话并立刻返回 runId。
 */

    @Operation(summary = "提交 /turns")
    @PostMapping("/turns")
    public ApiResponse<TurnAccepted> turns(@RequestAttribute("userId") String userId, @RequestBody TurnRequest request) {
        TurnAccepted accepted = turns.open(userId, request.conversationId(), request.clientMsgId(), request.text());
        return ApiResponse.ok(accepted, traceId(), now());
    }
/**
 * 把 Redis 里的事件按间隔泵成 SSE。客户端断开不中断工人。
 */

    @Operation(summary = "查询 /runs/{runId}/events")
    @GetMapping(value = "/runs/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @RequestAttribute("userId") String userId,
            @PathVariable String runId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        cancels.requireOwner(userId, runId);
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean open = new AtomicBoolean(true);
        emitter.onCompletion(() -> open.set(false));
        emitter.onTimeout(() -> {
            open.set(false);
            pump.onDisconnect(runId);
        });
        emitter.onError(ex -> {
            open.set(false);
            pump.onDisconnect(runId);
        });
        Thread.ofVirtual().start(() -> pump(emitter, open, runId, lastEventId));
        return emitter;
    }
/**
 * 取消进行中的运行。
 */

    @Operation(summary = "提交 /runs/{runId}/cancel")
    @PostMapping("/runs/{runId}/cancel")
    public ApiResponse<Void> cancel(@RequestAttribute("userId") String userId, @PathVariable String runId) {
        cancels.cancel(userId, runId);
        return ApiResponse.ok(null, traceId(), now());
    }
/**
 * 在超时内把新事件写给当前连接。终态后结束流。
 */

    private void pump(SseEmitter emitter, AtomicBoolean open, String runId, String afterId) {
        String after = afterId;
        try {
            while (open.get()) {
                List<ChatEvents.Event> batch = pump.read(runId, after);
                for (ChatEvents.Event event : batch) {
                    emitter.send(SseEmitter.event().id(event.id()).name(event.event()).data(event.data()));
                    after = event.id();
                    if (ChatEvents.TERMINAL.contains(event.event())) {
                        emitter.complete();
                        return;
                    }
                }
                Thread.sleep(pump.pollInterval().toMillis());
            }
        } catch (IOException | IllegalStateException ex) {
            pump.onDisconnect(runId);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            pump.onDisconnect(runId);
        }
    }

    private static String traceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String now() {
        return OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString();
    }
/**
 * 一轮对话的文本和客户端消息号。
 */

    public record TurnRequest(String conversationId, String clientMsgId, String inputType, String text, Device device) {
    }
/**
 * 开 run 时带上的端信息，用于归因。
 */

    public record Device(
            String os,
            String osVersion,
            String clientType,
            String channel,
            Integer versionCode,
            String versionName,
            List<String> installedPlatforms) {
    }
}
