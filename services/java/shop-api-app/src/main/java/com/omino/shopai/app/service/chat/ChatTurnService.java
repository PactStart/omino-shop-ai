package com.omino.shopai.app.service.chat;

import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.Conversation;
import com.omino.shopai.support.Ids;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;
/**
 * ChatTurnService 编排用例，事务和校验放在这里。
 */

@Service
public class ChatTurnService {

    private final ConversationStore conversations;
    private final ChatCancelService cancels;
    private final ChatWorker worker;
    private final Executor executor;
    private final Clock clock;
    private final Duration timeout;
    private final CreditGate credits;

    public ChatTurnService(
            ConversationStore conversations,
            ChatCancelService cancels,
            ChatWorker worker,
            Executor chatWorkerExecutor,
            Clock clock,
            Duration chatRunTimeout) {
        this(conversations, cancels, worker, chatWorkerExecutor, clock, chatRunTimeout, CreditGate.ALLOW);
    }
/**
 * 注入会话、取消标记、工人和积分门。开 run 不在控制器里做。
 */

    @Autowired
    public ChatTurnService(
            ConversationStore conversations,
            ChatCancelService cancels,
            ChatWorker worker,
            Executor chatWorkerExecutor,
            Clock clock,
            Duration chatRunTimeout,
            CreditGate credits) {
        this.conversations = conversations;
        this.cancels = cancels;
        this.worker = worker;
        this.executor = chatWorkerExecutor;
        this.clock = clock;
        this.timeout = chatRunTimeout;
        this.credits = credits;
    }
/**
 * 校验文本后按 clientMsgId 幂等。已受理则返回原来的 runId。
 */

    public TurnAccepted open(String userId, String conversationId, String clientMsgId, String text) {
        if (clientMsgId == null || clientMsgId.isBlank() || clientMsgId.length() > 64) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "clientMsgId 不能为空");
        }
        if (text == null || text.isBlank() || text.trim().length() > 2000) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "text 不能为空");
        }
        String trimmed = text.trim();
        Conversation conversation = resolveConversation(userId, conversationId, trimmed);
        return conversations.findRunId(conversation.getId(), clientMsgId)
                .map(runId -> new TurnAccepted(conversation.getId(), runId, clientMsgId))
                .orElseGet(() -> start(userId, conversation.getId(), clientMsgId, trimmed));
    }
/**
 * 写入用户消息并立刻返回 runId。积分不足时不把工人丢进线程池。
 */

    private TurnAccepted start(String userId, String conversationId, String clientMsgId, String text) {
        String runId = Ids.ulid();
        conversations.insertUserMessage(conversationId, clientMsgId, text, runId);
        ChatRun run = new ChatRun(runId, userId, conversationId, clientMsgId, text, clock.instant().plus(timeout));
        cancels.remember(run);
        if (!credits.block(run)) {
            executor.execute(() -> worker.start(run));
        }
        return new TurnAccepted(conversationId, runId, clientMsgId);
    }
/**
 * 沿用已有会话，或按本轮文本新建一条。
 */

    private Conversation resolveConversation(String userId, String conversationId, String text) {
        if (conversationId == null || conversationId.isBlank()) {
            String title = text.length() <= 32 ? text : text.substring(0, 32);
            return conversations.create(userId, title);
        }
        return conversations.findOwned(conversationId, userId)
                .orElseThrow(() -> new ApiBusinessException(ErrorCodes.NOT_FOUND, "会话不存在"));
    }
}
