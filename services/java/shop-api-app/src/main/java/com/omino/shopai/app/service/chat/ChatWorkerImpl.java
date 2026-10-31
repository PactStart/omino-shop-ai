package com.omino.shopai.app.service.chat;

import com.omino.shopai.exception.AgentUnavailableException;

import com.omino.shopai.api.ErrorCodes;
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

    private final IRunEventLog events;
    private final IAgentGateway gateway;
    private final Clock clock;
    private final Duration timeout;
    private final IRunContextLoader contexts;

    public ChatWorkerImpl(IRunEventLog events, IAgentGateway gateway, Clock clock, long timeoutSeconds) {
        this(events, gateway, clock, timeoutSeconds, IRunContextLoader.passthrough());
    }

    @Autowired
    public ChatWorkerImpl(
            IRunEventLog events,
            IAgentGateway gateway,
            Clock clock,
            @Value("${chat.run-timeout-seconds:120}") long timeoutSeconds,
            IRunContextLoader contexts) {
        this.events = events;
        this.gateway = gateway;
        this.clock = clock;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.contexts = contexts;
    }

    public Duration timeout() {
        return timeout;
    }
/**
 * 超时或上下文不可用时写 50020。取消只认 Redis 标记。
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
 */

    private void forward(ChatRun run, String name, String data) {
        if (events.cancelled(run.runId()) || events.terminal(run.runId())) {
            throw new AgentAborted();
        }
        if (!clock.instant().isBefore(run.deadline())) {
            throw new AgentUnavailableException();
        }
        events.append(run.runId(), name, data);
    }
/**
 * 写一条 error 事件并给流加过期，让 SSE 能收到终态。
 */

    private void writeError(String runId, String message) {
        events.append(runId, "error", "{\"code\":" + ErrorCodes.AGENT + ",\"message\":\"" + message + "\"}");
        events.expire(runId);
    }
}
