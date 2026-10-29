package com.omino.shopai.app.service.chat;
/**
 * 工人发现已取消或已终态，停止继续转发。
 */

public class AgentAborted extends RuntimeException {

    public AgentAborted() {
        super("run aborted");
    }
}
