package com.omino.shopai.app.service.chat;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 本地不调 Agent，直接写夹具事件。
 */
@Service
@Profile("local")
public class FixtureChatWorkerImpl implements IChatWorker {

    private final IChatEventService events;

    /**
     * 注入事件流。本地夹具只往这里写。
     *
     * @param events 事件流
     */
    public FixtureChatWorkerImpl(IChatEventService events) {
        this.events = events;
    }

    /**
     * 本地夹具直接写几条事件，不调用 Python。
     *
     * @param run 这一轮
     */
    @Override
    public void start(ChatRun run) {
        events.append(run.runId(), "text_delta", "{\"text\":\"你好\"}");
        events.append(run.runId(), "done", "{\"conversationId\":\"" + run.conversationId() + "\",\"serverMsgId\":\"local\",\"intent\":\"chitchat\"}");
        events.expire(run.runId());
    }
}
