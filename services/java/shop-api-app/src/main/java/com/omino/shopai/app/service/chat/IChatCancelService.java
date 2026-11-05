package com.omino.shopai.app.service.chat;

/**
 * 记住一轮的属主，并按属主取消。
 */
public interface IChatCancelService {

    /**
     * 记下这轮属于哪个用户，供之后取消和订阅核对。
     *
     * @param run 刚开的一轮
     */
    void remember(ChatRun run);

    /**
     * 只能操作自己的 run。别人的 runId 当不存在。
     *
     * @param userId 用户主键
     * @param runId 这一轮的编号
     */
    void requireOwner(String userId, String runId);

    /**
     * 取消进行中的运行。已经终态则不再写事件。
     *
     * @param userId 用户主键
     * @param runId 这一轮的编号
     */
    void cancel(String userId, String runId);
}
