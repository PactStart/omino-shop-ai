package com.omino.shopai.app.service.chat;

import java.time.Duration;
import java.util.List;
/**
 * 对话事件日志。断开 SSE 不得删流。
 */

public interface RunEventLog {

    ChatEvents.Event append(String runId, String event, String data);

    List<ChatEvents.Event> after(String runId, String afterId);

    void markCancel(String runId);

    boolean cancelled(String runId);

    boolean terminal(String runId);

    void expire(String runId);

    Duration ttl(String runId);

    void bind(String runId, String userId);

    java.util.Optional<String> owner(String runId);
}
