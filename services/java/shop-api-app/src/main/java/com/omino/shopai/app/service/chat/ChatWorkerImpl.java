package com.omino.shopai.app.service.chat;

import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.exception.AgentUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

/**
 * 调用 Agent 并把事件写入 Redis。取消只认标记。
 */
@Service
@Profile({"test", "prod"})
public class ChatWorkerImpl implements IChatWorker {

    private final IChatEventService events;
    private final IAgentGateway gateway;
    private final Clock clock;
    private final Duration timeout;
    private final IRunContextLoader contexts;
    private final IUsageBilling billing;

    /**
     * 注入事件流和 Agent。上下文默认原样放行。
     *
     * @param events 事件流
     * @param gateway Agent
     * @param clock 判断是否超时
     * @param timeoutSeconds 这一轮允许跑多少秒
     */
    public ChatWorkerImpl(IChatEventService events, IAgentGateway gateway, Clock clock, long timeoutSeconds) {
        this(events, gateway, clock, timeoutSeconds, IRunContextLoader.passthrough(), IUsageBilling.NONE);
    }

    /**
     * 注入事件流、Agent 和上下文。超时或上下文不可用时不调用 Agent。
     *
     * @param events 事件流
     * @param gateway Agent
     * @param clock 判断是否超时
     * @param timeoutSeconds 这一轮允许跑多少秒
     * @param contexts 提示词和模型
     */
    public ChatWorkerImpl(
            IChatEventService events,
            IAgentGateway gateway,
            Clock clock,
            long timeoutSeconds,
            IRunContextLoader contexts) {
        this(events, gateway, clock, timeoutSeconds, contexts, IUsageBilling.NONE);
    }

    /**
     * 注入事件流、Agent、上下文和用量扣费。超时或上下文不可用时不调用 Agent。
     *
     * @param events 事件流
     * @param gateway Agent
     * @param clock 判断是否超时
     * @param timeoutSeconds 这一轮允许跑多少秒
     * @param contexts 提示词和模型
     * @param billing 第一次转发用量时扣费
     */
    @Autowired
    public ChatWorkerImpl(
            IChatEventService events,
            IAgentGateway gateway,
            Clock clock,
            @Value("${chat.run-timeout-seconds:120}") long timeoutSeconds,
            IRunContextLoader contexts,
            IUsageBilling billing) {
        this.events = events;
        this.gateway = gateway;
        this.clock = clock;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.contexts = contexts;
        this.billing = billing;
    }

    /**
     * 这一轮允许跑多久。
     *
     * @return 超时时间
     */
    public Duration timeout() {
        return timeout;
    }

    /**
     * 超时或上下文不可用时写 50020。取消只认 Redis 标记。
     *
     * @param run 这一轮
     */
    @Override
    public void start(ChatRun run) {
        if (!clock.instant().isBefore(run.deadline())) {
            writeError(run.runId(), "对话超时");
            return;
        }
        RunContext context = contexts.load(run);
        // 提示词或模型不可用时不调用 Agent，直接终态 50020。
        if (context == null) {
            writeError(run.runId(), "Agent 不可用");
            return;
        }
        try {
            gateway.stream(run, context, (name, data) -> forward(run, name, data));
            if (events.terminal(run.runId())) {
                events.expire(run.runId());
            } else if (!events.cancelled(run.runId())) {
                writeError(run.runId(), "Agent 不可用");
            }
        } catch (AgentAborted ex) {
            // 取消只认 Redis 标记。调用方断开 SSE 不会进到这里。
            return;
        } catch (AgentUnavailableException ex) {
            if (!events.terminal(run.runId())) {
                writeError(run.runId(), "Agent 不可用");
            }
        }
    }

    /**
     * 终态或已取消则停止转发。超时当成 Agent 不可用。
     *
     * @param run 这一轮
     * @param name 事件名
     * @param data 事件正文
     */
    private void forward(ChatRun run, String name, String data) {
        if (events.cancelled(run.runId()) || events.terminal(run.runId())) {
            throw new AgentAborted();
        }
        if (!clock.instant().isBefore(run.deadline())) {
            throw new AgentUnavailableException();
        }
        events.append(run.runId(), name, data);
        billing.onEvent(run, name, data);
    }

    /**
     * 写一条 error 事件并给流加过期，让 SSE 能收到终态。
     *
     * @param runId 这一轮的编号
     * @param message 错误说明
     */
    private void writeError(String runId, String message) {
        events.append(runId, "error", "{\"code\":" + ErrorCodes.AGENT + ",\"message\":\"" + message + "\"}");
        events.expire(runId);
    }
}
