package com.omino.shopai.app.service.chat;

import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.exception.AgentUnavailableException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 本地先调助手。只有还没拿到响应头时才写固定问候。
 */
@Service
@Profile("local")
public class LocalChatWorkerImpl implements IChatWorker {

    private final IChatEventService events;
    private final IAgentGateway gateway;
    private final IRunContextLoader contexts;
    private final IUsageBilling billing;
    private final FixtureChatWorkerImpl greeting = new FixtureChatWorkerImpl();

    /**
     * 注入事件流、助手和上下文。
     *
     * @param events 事件流
     * @param gateway 助手
     * @param contexts 提示词和模型
     * @param billing 用量扣费
     */
    public LocalChatWorkerImpl(
            IChatEventService events,
            IAgentGateway gateway,
            IRunContextLoader contexts,
            IUsageBilling billing) {
        this.events = events;
        this.gateway = gateway;
        this.contexts = contexts;
        this.billing = billing;
    }

    /**
     * 连接被拒绝或响应头之前超时才问候。已经接到 HTTP 状态则写助手不可用。
     *
     * @param run 这一轮
     */
    @Override
    public void start(ChatRun run) {
        RunContext context = contexts.load(run);
        if (context == null) {
            writeError(run.runId());
            return;
        }
        boolean[] forwarded = {false};
        try {
            gateway.stream(run, context, (name, data) -> {
                forwarded[0] = true;
                forward(run, name, data);
            });
            if (!events.terminal(run.runId()) && !events.cancelled(run.runId())) {
                writeError(run.runId());
            } else if (events.terminal(run.runId())) {
                events.expire(run.runId());
            }
        } catch (AgentAborted ex) {
            return;
        } catch (AgentUnavailableException ex) {
            if (events.terminal(run.runId())) {
                return;
            }
            // 已经转发过的事件不能再用问候覆盖。
            if (ex.beforeHeaders() && !forwarded[0]) {
                greeting.writeGreeting(events, run);
            } else {
                writeError(run.runId());
            }
        }
    }

    /**
     * 终态或已取消则停止转发。用量在第一次写入时结算。
     *
     * @param run 这一轮
     * @param name 事件名
     * @param data 事件正文
     */
    private void forward(ChatRun run, String name, String data) {
        if (events.cancelled(run.runId()) || events.terminal(run.runId())) {
            throw new AgentAborted();
        }
        events.append(run.runId(), name, data);
        billing.onEvent(run, name, data);
    }

    /**
     * 写一条助手不可用并结束这一轮。
     *
     * @param runId 这一轮的编号
     */
    private void writeError(String runId) {
        events.append(runId, "error", "{\"code\":" + ErrorCodes.AGENT + ",\"message\":\"Agent 不可用\"}");
        events.expire(runId);
    }
}
