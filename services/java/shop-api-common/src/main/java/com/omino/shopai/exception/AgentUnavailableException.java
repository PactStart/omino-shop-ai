package com.omino.shopai.exception;
/**
 * Agent 没接上或超时。调用方写成 50020。
 */

public class AgentUnavailableException extends RuntimeException {

    private final boolean beforeHeaders;

    /**
     * 已经拿到响应，或无法判断是不是还没连上。不当成固定问候。
     */
    public AgentUnavailableException() {
        this(false);
    }

    /**
     * 区分还没拿到响应头，和已经接到 HTTP 状态。
     *
     * @param beforeHeaders 连接被拒绝或响应头之前超时为真
     */
    public AgentUnavailableException(boolean beforeHeaders) {
        super("Agent 不可用");
        this.beforeHeaders = beforeHeaders;
    }

    /**
     * 是否在响应头之前就失败。只有这种失败才允许本地固定问候。
     *
     * @return 还没拿到响应头时为真
     */
    public boolean beforeHeaders() {
        return beforeHeaders;
    }
}
