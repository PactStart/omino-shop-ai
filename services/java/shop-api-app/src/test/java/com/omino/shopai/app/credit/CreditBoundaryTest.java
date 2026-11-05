package com.omino.shopai.app.credit;

import com.omino.shopai.api.ApiExceptionHandler;
import com.omino.shopai.app.adapter.pay.AlipayNotifyImpl;
import com.omino.shopai.app.adapter.pay.WechatPayNotifyImpl;
import com.omino.shopai.app.service.chat.ChatCancelServiceImpl;
import com.omino.shopai.app.service.chat.ChatEventServiceImpl;
import com.omino.shopai.app.service.chat.ChatEvents;
import com.omino.shopai.app.service.chat.ChatTurnServiceImpl;
import com.omino.shopai.app.service.chat.IChatCancelService;
import com.omino.shopai.app.service.chat.IChatWorker;
import com.omino.shopai.app.service.chat.ICreditGate;
import com.omino.shopai.app.dto.TurnAcceptedDto;
import com.omino.shopai.app.service.credit.ICreditPurchaseService;
import com.omino.shopai.app.web.app.CreditController;
import com.omino.shopai.entity.Conversation;
import com.omino.shopai.support.Ids;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CreditBoundaryTest {

    @Test
    void lowBalanceStillReturnsRunAndEndsWithCreditExhausted() {
        MemoryEvents store = new MemoryEvents();
        MemoryEventService events = new MemoryEventService(store);
        MemoryCredits books = new MemoryCredits();
        MemoryBilling billing = new MemoryBilling(books, events, true);
        QueuedExecutor executor = new QueuedExecutor();
        AtomicInteger workerCalls = new AtomicInteger();
        ChatTurnServiceImpl turns = new MemoryTurns(
                new MemoryConversations(),
                new ChatCancelServiceImpl(events),
                run -> workerCalls.incrementAndGet(),
                executor,
                Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneId.of("UTC")),
                Duration.ofSeconds(30),
                billing);
        TurnAcceptedDto accepted = turns.open("01USER0000000000000000000A", null, "msg-1", "想买鞋");
        assertFalse(accepted.runId().isBlank());
        assertEquals(0, executor.pending());
        assertEquals(0, workerCalls.get());
        List<ChatEvents.Event> written = events.after(accepted.runId(), null);
        assertTrue(written.stream().anyMatch(event -> "credit_card".equals(event.event())));
        assertEquals("credit_exhausted", written.get(written.size() - 1).event());
        assertTrue(written.get(written.size() - 1).data().contains("INSUFFICIENT_START"));
    }

    @Test
    void creditsCannotMoveBackToWallet() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/omino/shopai/app/web/app/CreditController.java"));
        assertFalse(source.contains("withdraw"));
        assertFalse(source.contains("to-wallet"));
        MemoryCredits books = new MemoryCredits();
        ICreditPurchaseService purchases = new MemoryPurchase(books, Clock.systemUTC());
        MemoryBilling billing = new MemoryBilling(books, new MemoryEventService(new MemoryEvents()), false);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CreditController(
                        purchases,
                        billing,
                        new WechatPayNotifyImpl("", "", ""),
                        new AlipayNotifyImpl("")))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        String withdraw = mvc.perform(post("/v1/credits/withdraw").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString();
        String convert = mvc.perform(post("/v1/credits/to-wallet").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(withdraw.contains("\"code\":0"));
        assertFalse(convert.contains("\"code\":0"));
    }

    private static final class MemoryConversations {
        /**
         * 建一条只活在内存里的会话。
         *
         * @param userId 用户主键
         * @param title 会话标题
         * @return 会话
         */
        public Conversation create(String userId, String title) {
            Conversation conversation = new Conversation();
            conversation.setId(Ids.ulid());
            conversation.setUserId(userId);
            conversation.setTitle(title);
            return conversation;
        }

        /**
         * 这条测试不回放已有会话。
         *
         * @param conversationId 会话主键
         * @param userId 用户主键
         * @return 空
         */
        public Optional<Conversation> findOwned(String conversationId, String userId) {
            return Optional.empty();
        }

        /**
         * 开 run 会写消息。这条测试不核对消息正文。
         *
         * @param conversationId 会话主键
         * @param clientMsgId 客户端消息号
         * @param text 用户文本
         * @param runId 这一轮的编号
         */
        public void insertUserMessage(String conversationId, String clientMsgId, String text, String runId) {
        }

        /**
         * 这条测试不回放已受理的 run。
         *
         * @param conversationId 会话主键
         * @param clientMsgId 客户端消息号
         * @return 空
         */
        public Optional<String> findRunId(String conversationId, String clientMsgId) {
            return Optional.empty();
        }
    }

    /**
     * 开 run 仍走真实受理，会话表换成内存。
     */
    private static final class MemoryTurns extends ChatTurnServiceImpl {
        private final MemoryConversations conversations;

        /**
         * 绑定内存会话。
         *
         * @param conversations 内存会话
         * @param cancels 取消与属主
         * @param worker 工人
         * @param executor 工人线程
         * @param clock 截止时间
         * @param timeout 这一轮允许跑多久
         * @param credits 开 run 前的积分门
         */
        private MemoryTurns(
                MemoryConversations conversations,
                IChatCancelService cancels,
                IChatWorker worker,
                Executor executor,
                Clock clock,
                Duration timeout,
                ICreditGate credits) {
            super(null, null, null, cancels, worker, executor, clock, timeout, credits);
            this.conversations = conversations;
        }

        /**
         * 从内存会话新建。
         *
         * @param userId 用户主键
         * @param title 会话标题
         * @return 会话
         */
        @Override
        protected Conversation createConversation(String userId, String title) {
            return conversations.create(userId, title);
        }

        /**
         * 从内存会话查找属主。
         *
         * @param conversationId 会话主键
         * @param userId 用户主键
         * @return 会话；没有则空
         */
        @Override
        protected Optional<Conversation> findOwnedConversation(String conversationId, String userId) {
            return conversations.findOwned(conversationId, userId);
        }

        /**
         * 把用户消息交给内存会话。
         *
         * @param conversationId 会话主键
         * @param clientMsgId 客户端消息号
         * @param text 用户文本
         * @param runId 这一轮的编号
         */
        @Override
        protected void insertUserMessage(String conversationId, String clientMsgId, String text, String runId) {
            conversations.insertUserMessage(conversationId, clientMsgId, text, runId);
        }

        /**
         * 从内存会话找回 runId。
         *
         * @param conversationId 会话主键
         * @param clientMsgId 客户端消息号
         * @return runId；没有则空
         */
        @Override
        protected Optional<String> findRunId(String conversationId, String clientMsgId) {
            return conversations.findRunId(conversationId, clientMsgId);
        }
    }

    private static final class QueuedExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        int pending() {
            return tasks.size();
        }
    }

    private static final class MemoryEvents {
        private final Map<String, List<ChatEvents.Event>> streams = new HashMap<>();
        private final Map<String, Integer> sequences = new HashMap<>();
        private final Set<String> cancels = new HashSet<>();
        private final Map<String, String> owners = new HashMap<>();

        public ChatEvents.Event append(String runId, String event, String data) {
            ChatEvents.check(event);
            int next = sequences.merge(runId, 1, Integer::sum);
            ChatEvents.Event item = new ChatEvents.Event(Integer.toString(next), event, data);
            streams.computeIfAbsent(runId, key -> new ArrayList<>()).add(item);
            return item;
        }

        public List<ChatEvents.Event> after(String runId, String afterId) {
            long after = afterId == null || afterId.isBlank() ? 0 : Long.parseLong(afterId);
            return streams.getOrDefault(runId, List.of()).stream()
                    .filter(event -> Long.parseLong(event.id()) > after)
                    .toList();
        }

        public void markCancel(String runId) {
            cancels.add(runId);
        }

        public boolean cancelled(String runId) {
            return cancels.contains(runId);
        }

        public boolean terminal(String runId) {
            return after(runId, null).stream().anyMatch(event -> ChatEvents.TERMINAL.contains(event.event()));
        }

        public void expire(String runId) {
        }

        public Duration ttl(String runId) {
            return null;
        }

        public void bind(String runId, String userId) {
            owners.put(runId, userId);
        }

        public Optional<String> owner(String runId) {
            return Optional.ofNullable(owners.get(runId));
        }
    }

    /**
     * 事件名校验仍走真实服务，流本身换成内存。
     */
    private static final class MemoryEventService extends ChatEventServiceImpl {
        private final MemoryEvents events;

        /**
         * 绑定内存事件流。
         *
         * @param events 内存事件
         */
        private MemoryEventService(MemoryEvents events) {
            super(null);
            this.events = events;
        }

        /**
         * 追加到内存流。
         *
         * @param runId 这一轮的编号
         * @param event 事件名
         * @param data 事件正文
         * @return 已写入的事件
         */
        @Override
        protected ChatEvents.Event appendEvent(String runId, String event, String data) {
            return events.append(runId, event, data);
        }

        /**
         * 从内存流续读。
         *
         * @param runId 这一轮的编号
         * @param afterId 已读到的序号
         * @return 序号更大的事件
         */
        @Override
        protected List<ChatEvents.Event> readAfter(String runId, String afterId) {
            return events.after(runId, afterId);
        }

        /**
         * 在内存里记下取消。
         *
         * @param runId 这一轮的编号
         */
        @Override
        protected void writeCancel(String runId) {
            events.markCancel(runId);
        }

        /**
         * 读取内存里的取消标记。
         *
         * @param runId 这一轮的编号
         * @return 已取消时为真
         */
        @Override
        protected boolean readCancelled(String runId) {
            return events.cancelled(runId);
        }

        /**
         * 内存流不设过期。
         *
         * @param runId 这一轮的编号
         */
        @Override
        protected void expireRun(String runId) {
            events.expire(runId);
        }

        /**
         * 读取内存流的剩余时间。
         *
         * @param runId 这一轮的编号
         * @return 剩余时间
         */
        @Override
        protected Duration readTtl(String runId) {
            return events.ttl(runId);
        }

        /**
         * 在内存里记下属主。
         *
         * @param runId 这一轮的编号
         * @param userId 用户主键
         */
        @Override
        protected void bindOwner(String runId, String userId) {
            events.bind(runId, userId);
        }

        /**
         * 读取内存里的属主。
         *
         * @param runId 这一轮的编号
         * @return 用户主键；没有则空
         */
        @Override
        protected Optional<String> readOwner(String runId) {
            return events.owner(runId);
        }
    }
}
