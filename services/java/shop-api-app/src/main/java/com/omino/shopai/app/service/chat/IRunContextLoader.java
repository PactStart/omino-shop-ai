package com.omino.shopai.app.service.chat;
/**
 * 为一次调用准备提示词和模型。缺关键项则返回空。
 */

public interface IRunContextLoader {

    static IRunContextLoader passthrough() {
        return run -> RunContext.passthrough();
    }

    RunContext load(ChatRun run);
}
