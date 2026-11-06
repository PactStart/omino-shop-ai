package com.omino.shopai.app.service.chat;

/**
 * 只负责在助手还没接上时写固定问候。不再作为 local 的工人。
 */
public class FixtureChatWorkerImpl {

    /**
     * 写固定问候和闲聊终态。
     *
     * @param events 事件流
     * @param run 这一轮
     */
    public void writeGreeting(IChatEventService events, ChatRun run) {
        events.append(run.runId(), "text_delta", "{\"text\":\"你好\"}");
        events.append(run.runId(), "done", "{\"conversationId\":\"" + run.conversationId() + "\",\"serverMsgId\":\"local\",\"intent\":\"chitchat\"}");
        events.expire(run.runId());
    }
}
