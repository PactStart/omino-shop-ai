package com.omino.shopai.app.service.chat;
/**
 * 调 Agent 的端口。实现只在 test 和 prod 装配。
 */

public interface IAgentGateway {

    void stream(ChatRun run, AgentSink sink);

    default void stream(ChatRun run, RunContext context, AgentSink sink) {
        stream(run, sink);
    }
/**
 * Agent 每吐一个事件就回调一次。
 */

    interface AgentSink {
        void event(String name, String data);
    }
}
