package com.omino.shopai.app.service.chat;

import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.exception.ApiBusinessException;
import org.springframework.stereotype.Service;

/**
 * 取消只认 Redis 标记。别人的 run 当不存在。
 */
@Service
public class ChatCancelServiceImpl implements IChatCancelService {

    private final IChatEventService events;

    /**
     * 注入事件流。属主和取消标记都写在那里。
     *
     * @param events 事件流
     */
    public ChatCancelServiceImpl(IChatEventService events) {
        this.events = events;
    }

    /**
     * 记下这轮属于哪个用户，供之后取消和订阅核对。
     *
     * @param run 刚开的一轮
     */
    @Override
    public void remember(ChatRun run) {
        events.bind(run.runId(), run.userId());
    }

    /**
     * 只能操作自己的 run。别人的 runId 当不存在。
     *
     * @param userId 用户主键
     * @param runId 这一轮的编号
     */
    @Override
    public void requireOwner(String userId, String runId) {
        String owner = events.owner(runId).orElse(null);
        if (owner == null || !owner.equals(userId)) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "对话不存在");
        }
    }

    /**
     * 取消进行中的运行。已经终态则不再写事件。
     *
     * @param userId 用户主键
     * @param runId 这一轮的编号
     */
    @Override
    public void cancel(String userId, String runId) {
        requireOwner(userId, runId);
        if (events.terminal(runId)) {
            return;
        }
        events.markCancel(runId);
        events.append(runId, "cancelled", "{\"runId\":\"" + runId + "\"}");
        events.expire(runId);
    }
}
