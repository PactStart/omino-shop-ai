package com.omino.shopai.app.service.chat;

/**
 * 按本轮累计用量扣积分。助手自己不改余额。
 */
public interface IUsageBilling {

    /**
     * 不扣费。没有计费实现时工人用它。
     */
    IUsageBilling NONE = (run, name, data) -> {
    };

    /**
     * 看到用量或终态时结算这一轮。
     *
     * @param run 这一轮
     * @param name 事件名
     * @param data 事件正文
     */
    void onEvent(ChatRun run, String name, String data);
}
