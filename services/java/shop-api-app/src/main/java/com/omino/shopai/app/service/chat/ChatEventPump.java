package com.omino.shopai.app.service.chat;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 按固定间隔把 Redis 事件写成 SSE。
 */
@Component
public class ChatEventPump {

    /**
     * 两次读取之间的间隔。
     */
    public static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    private final IChatEventService events;

    /**
     * 注入事件流。断开连接不在这里取消生成。
     *
     * @param events 事件流
     */
    public ChatEventPump(IChatEventService events) {
        this.events = events;
    }

    /**
     * 两次读取之间等多久。
     *
     * @return 轮询间隔
     */
    public Duration pollInterval() {
        return POLL_INTERVAL;
    }

    /**
     * 从给定序号之后读取新事件。
     *
     * @param runId 这一轮的编号
     * @param afterId 已读到的序号，空表示从头
     * @return 新事件
     */
    public List<ChatEvents.Event> read(String runId, String afterId) {
        return events.after(runId, afterId);
    }

    /**
     * 断开只停止读取。不写取消标记。
     *
     * @param runId 这一轮的编号
     */
    public void onDisconnect(String runId) {
        // 断开只停止读取。不写 chat:run:{runId}:cancel。
    }
}
