package com.omino.shopai.app.service.chat;
/**
 * Agent 没接上或超时。调用方写成 50020。
 */

public class AgentUnavailableException extends RuntimeException {

    public AgentUnavailableException() {
        super("Agent 不可用");
    }
}
