package com.omino.shopai.app.web.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.service.chat.ChatEventPump;
import com.omino.shopai.app.service.chat.ChatEvents;
import com.omino.shopai.app.service.chat.IChatCancelService;
import com.omino.shopai.app.service.chat.IChatTurnService;
import com.omino.shopai.app.service.chat.TurnCommand;
import com.omino.shopai.app.dto.ConversationViewDto;
import com.omino.shopai.app.dto.TurnAcceptedDto;
import com.omino.shopai.dto.PageDto;
import com.omino.shopai.app.web.app.dto.TurnRequestDto;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * ChatController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "ChatController")
@RequestMapping("/v1/chat")
public class ChatController {

    private final IChatTurnService turns;
    private final ChatEventPump pump;
    private final IChatCancelService cancels;
    /**
     * 开 run、泵 SSE、取消。断开连接不在这里取消生成。
     *
     * @param turns 受理一轮对话
     * @param pump 把事件写成 SSE
     * @param cancels 核对属主并取消
     */
    public ChatController(IChatTurnService turns, ChatEventPump pump, IChatCancelService cancels) {
        this.turns = turns;
        this.pump = pump;
        this.cancels = cancels;
    }
    /**
     * 按更新时间倒序列出当前用户的会话。
     *
     * @param userId 当前用户
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @return 会话分页信封
     */
    @Operation(summary = "列出我的会话")
    @GetMapping("/conversations")
    public ApiResponse<PageDto<ConversationViewDto>> conversations(
            @RequestAttribute("userId") String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(turns.list(userId, page, size));
    }

    /**
     * 受理一轮对话并立刻返回 run。
     *
     * @param userId 当前用户
     * @param request 文本和客户端消息号
     * @return 已受理的 run 信封
     */
    @Operation(summary = "提交一轮对话")
    @PostMapping("/turns")
    public ApiResponse<TurnAcceptedDto> turns(@RequestAttribute("userId") String userId, @RequestBody TurnRequestDto request) {
        List<String> installed = request.installedPlatforms();
        if ((installed == null || installed.isEmpty()) && request.device() != null && request.device().installedPlatforms() != null) {
            installed = request.device().installedPlatforms();
        }
        TurnAcceptedDto accepted = turns.open(userId, new TurnCommand(
                request.conversationId(),
                request.clientMsgId(),
                request.inputType(),
                request.text(),
                request.sourceUrl(),
                request.imageObjectKey(),
                request.hitl(),
                installed == null ? List.of() : new ArrayList<>(installed)));
        return ApiResponse.ok(accepted);
    }
    /**
     * 订阅一轮对话的事件流。断开连接不打断生成。
     *
     * @param userId 当前用户
     * @param runId 这一轮的编号
     * @param lastEventId 已收到的最后事件号，可空
     * @return 事件流
     */
    @Operation(summary = "订阅一轮对话的事件")
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
     * 请求取消一轮尚未结束的对话。
     *
     * @param userId 当前用户
     * @param runId 这一轮的编号
     * @return 空信封
     */
    @Operation(summary = "取消一轮对话")
    @PostMapping("/runs/{runId}/cancel")
    public ApiResponse<Void> cancel(@RequestAttribute("userId") String userId, @PathVariable String runId) {
        cancels.cancel(userId, runId);
        return ApiResponse.ok(null);
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
}
