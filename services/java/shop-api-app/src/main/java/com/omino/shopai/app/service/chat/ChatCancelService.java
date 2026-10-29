package com.omino.shopai.app.service.chat;

import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import org.springframework.stereotype.Service;
/**
 * ChatCancelService 编排用例，事务和校验放在这里。
 */

@Service
public class ChatCancelService {

    private final RunEventLog events;

    public ChatCancelService(RunEventLog events) {
        this.events = events;
    }

    public void remember(ChatRun run) {
        events.bind(run.runId(), run.userId());
    }
/**
 * 只能取消自己的 run。别人的 runId 当不存在。
 */

    public void requireOwner(String userId, String runId) {
        String owner = events.owner(runId).orElse(null);
        if (owner == null || !owner.equals(userId)) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "对话不存在");
        }
    }
/**
 * 取消进行中的运行。
 */

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
