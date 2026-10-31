package com.omino.shopai.app.service.chat;
/**
 * 真正跑一轮的人。local 用夹具，其它环境调 Agent。
 */

public interface IChatWorker {

    void start(ChatRun run);
}
