package com.omino.shopai.app.service.chat;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
/**
 * 按固定间隔把 Redis 事件写成 SSE。
 */

@Component
public class ChatEventPump {

    public static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    private final IRunEventLog events;

    public ChatEventPump(IRunEventLog events) {
        this.events = events;
    }

    public Duration pollInterval() {
        return POLL_INTERVAL;
    }

    public List<ChatEvents.Event> read(String runId, String afterId) {
        return events.after(runId, afterId);
    }

    public void onDisconnect(String runId) {
        // 断开只停止读取。不写 chat:run:{runId}:cancel。
    }
}
