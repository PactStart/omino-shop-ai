package com.omino.shopai.app.service.chat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 一轮对话的事件流。断开 SSE 不得删流。
 */
public interface IChatEventService {

    /**
     * 把事件追加进该 run 的流，供任意副本的 SSE 读取。
     *
     * @param runId 这一轮的编号
     * @param event 事件名
     * @param data 事件正文，可空
     * @return 已写入的事件
     */
    ChatEvents.Event append(String runId, String event, String data);

    /**
     * 从给定序号之后读取，泵中断后可以续上。
     *
     * @param runId 这一轮的编号
     * @param afterId 已读到的序号，空表示从头
     * @return 序号更大的事件
     */
    List<ChatEvents.Event> after(String runId, String afterId);

    /**
     * 记下取消标记。
     *
     * @param runId 这一轮的编号
     */
    void markCancel(String runId);

    /**
     * 这一轮是否已被取消。
     *
     * @param runId 这一轮的编号
     * @return 已取消时为真
     */
    boolean cancelled(String runId);

    /**
     * 流里是否已有终态事件。
     *
     * @param runId 这一轮的编号
     * @return 已结束时为真
     */
    boolean terminal(String runId);

    /**
     * 终态后给流一个过期时间，避免 Redis 被旧 run 撑满。
     *
     * @param runId 这一轮的编号
     */
    void expire(String runId);

    /**
     * 读剩余存活时间。没有键则按已结束处理。
     *
     * @param runId 这一轮的编号
     * @return 剩余时间；没有键则空
     */
    Duration ttl(String runId);

    /**
     * 记下这轮属于哪个用户。
     *
     * @param runId 这一轮的编号
     * @param userId 用户主键
     */
    void bind(String runId, String userId);

    /**
     * 读取这轮的属主。
     *
     * @param runId 这一轮的编号
     * @return 用户主键；没有则空
     */
    Optional<String> owner(String runId);
}
