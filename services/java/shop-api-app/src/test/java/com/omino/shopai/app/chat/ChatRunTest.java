package com.omino.shopai.app.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.api.ApiExceptionHandler;
import com.omino.shopai.app.service.chat.IAgentGateway;
import com.omino.shopai.exception.AgentUnavailableException;
import com.omino.shopai.app.service.chat.ChatCancelServiceImpl;
import com.omino.shopai.app.service.chat.ChatEventPump;
import com.omino.shopai.app.service.chat.ChatEventServiceImpl;
import com.omino.shopai.app.service.chat.ChatEvents;
import com.omino.shopai.app.service.chat.ChatRun;
import com.omino.shopai.app.service.chat.ChatTurnServiceImpl;
import com.omino.shopai.app.service.chat.ChatWorkerImpl;
import com.omino.shopai.app.service.chat.IChatCancelService;
import com.omino.shopai.app.service.chat.IChatWorker;
import com.omino.shopai.app.service.chat.FixtureChatWorkerImpl;
import com.omino.shopai.app.service.chat.AgentGatewayImpl;
import com.omino.shopai.app.dto.TurnAcceptedDto;
import com.omino.shopai.app.web.app.ChatController;
import com.omino.shopai.entity.Conversation;
import com.omino.shopai.support.Ids;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatRunTest {

    private static final String USER = "01USER00000000000000000001";
    private static final ObjectMapper JSON = new ObjectMapper();

    private MemoryEventLog store;
    private MemoryEvents events;
    private MemoryConversations conversations;
    private ScriptedGateway gateway;
    private QueuedExecutor executor;
    private MutableClock clock;
    private ChatCancelServiceImpl cancels;
    private MemoryTurns turns;
    private ChatEventPump pump;
    private ChatWorkerImpl worker;

    @BeforeEach
    void setUp() {
        store = new MemoryEventLog();
        events = new MemoryEvents(store);
        conversations = new MemoryConversations();
        gateway = new ScriptedGateway();
        executor = new QueuedExecutor();
        clock = new MutableClock(Instant.parse("2026-10-29T14:00:00Z"));
        cancels = new ChatCancelServiceImpl(events);
        worker = new ChatWorkerImpl(events, gateway, clock, 30);
        turns = new MemoryTurns(conversations, cancels, worker, executor, clock, Duration.ofSeconds(30));
        pump = new ChatEventPump(events);
    }

    @Test
    void postReturnsRunIdBeforeWorkerRuns() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ChatController(turns, pump, cancels))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        mvc.perform(post("/v1/chat/turns")
                        .requestAttr("userId", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientMsgId":"c1","inputType":"text","text":"找耳机","device":{"os":"ios","osVersion":"17.0","clientType":"h5","channel":"web","versionCode":20261029,"versionName":"0.1.0","installedPlatforms":["jd"]}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.clientMsgId").value("c1"))
                .andExpect(jsonPath("$.data.runId").isNotEmpty());
        assertEquals(1, executor.pending());
        assertTrue(store.streams.isEmpty());
    }

    @Test
    void sameClientMessageReturnsTheSameRun() {
        TurnAcceptedDto first = turns.open(USER, null, "c1", "找耳机");
        TurnAcceptedDto second = turns.open(USER, first.conversationId(), "c1", "找耳机");
        assertEquals(first.runId(), second.runId());
        assertEquals(1, executor.pending());
    }

    @Test
    void disconnectKeepsEventsAndDoesNotCancel() {
        gateway.script = sink -> {
            sink.event("text_delta", "{\"text\":\"你好\"}");
            sink.event("done", "{\"conversationId\":\"c\",\"serverMsgId\":\"m\",\"intent\":\"shop.recommend\"}");
        };
        TurnAcceptedDto accepted = turns.open(USER, null, "c1", "找耳机");
        executor.drain();
        List<ChatEvents.Event> written = pump.read(accepted.runId(), null);
        assertFalse(written.isEmpty());
        written.forEach(event -> assertTrue(ChatEvents.NAMES.contains(event.event())));
        pump.onDisconnect(accepted.runId());
        assertFalse(events.cancelled(accepted.runId()));
        assertTrue(pump.read(accepted.runId(), null).stream().anyMatch(event -> "text_delta".equals(event.event())));
        assertTrue(pump.pollInterval().compareTo(Duration.ofMillis(100)) <= 0);
    }

    @Test
    void cancelWritesCancelledAndDropsLaterDone() throws Exception {
        String[] runId = new String[1];
        gateway.script = sink -> {
            sink.event("text_delta", "{\"text\":\"你好\"}");
            cancels.cancel(USER, runId[0]);
            sink.event("done", "{}");
        };
        TurnAcceptedDto accepted = turns.open(USER, null, "c1", "找耳机");
        runId[0] = accepted.runId();
        executor.drain();
        List<ChatEvents.Event> written = events.after(accepted.runId(), null);
        assertEquals(1, written.stream().filter(event -> "text_delta".equals(event.event())).count());
        assertEquals(1, written.stream().filter(event -> "cancelled".equals(event.event())).count());
        assertEquals(0, written.stream().filter(event -> "done".equals(event.event())).count());
        assertEquals(ChatEvents.TERMINAL_TTL, events.ttl(accepted.runId()));
        cancels.cancel(USER, accepted.runId());
        assertEquals(1, events.after(accepted.runId(), null).stream().filter(event -> "cancelled".equals(event.event())).count());
        JsonNode data = JSON.readTree(written.stream().filter(event -> "cancelled".equals(event.event())).findFirst().orElseThrow().data());
        assertEquals(accepted.runId(), data.get("runId").asText());
    }

    @Test
    void agentDownEndsWithError50020AndNoSampleDone() throws Exception {
        gateway.script = sink -> {
            throw new AgentUnavailableException();
        };
        TurnAcceptedDto accepted = turns.open(USER, null, "c1", "找耳机");
        executor.drain();
        List<ChatEvents.Event> written = events.after(accepted.runId(), null);
        assertEquals(List.of("error"), written.stream().map(ChatEvents.Event::event).toList());
        JsonNode data = JSON.readTree(written.get(0).data());
        assertEquals(50020, data.get("code").asInt());
        assertEquals(ChatEvents.TERMINAL_TTL, events.ttl(accepted.runId()));
    }

    @Test
    void expiredRunWritesTimeoutWithoutCallingAgent() {
        TurnAcceptedDto accepted = turns.open(USER, null, "c1", "找耳机");
        clock.set(Instant.parse("2026-10-29T14:00:31Z"));
        executor.drain();
        assertEquals(0, gateway.calls);
        ChatEvents.Event error = events.after(accepted.runId(), null).get(0);
        assertEquals("error", error.event());
        assertTrue(error.data().contains("50020"));
        assertTrue(error.data().contains("对话超时"));
        assertFalse(events.after(accepted.runId(), null).stream().anyMatch(event -> "done".equals(event.event())));
    }

    @Test
    void fixtureWorkerIsLocalOnly() {
        assertEquals(List.of("local"), List.of(FixtureChatWorkerImpl.class.getAnnotation(Profile.class).value()));
        assertFalse(List.of(ChatWorkerImpl.class.getAnnotation(Profile.class).value()).contains("local"));
        assertTrue(List.of(ChatWorkerImpl.class.getAnnotation(Profile.class).value()).containsAll(List.of("test", "prod")));
        assertFalse(List.of(AgentGatewayImpl.class.getAnnotation(Profile.class).value()).contains("local"));
    }

    @Test
    void unknownEventNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> events.append("run", "sample_done", "{}"));
    }

    private static final class ScriptedGateway implements IAgentGateway {
        int calls;
        Consumer<AgentSink> script = sink -> {
        };

        @Override
        public void stream(ChatRun run, AgentSink sink) {
            calls++;
            script.accept(sink);
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

        void drain() {
            Runnable task;
            while ((task = tasks.poll()) != null) {
                task.run();
            }
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class MemoryEventLog {
        private final Map<String, List<ChatEvents.Event>> streams = new HashMap<>();
        private final Map<String, Integer> sequences = new HashMap<>();
        private final Set<String> cancels = new HashSet<>();
        private final Map<String, String> owners = new HashMap<>();
        private final Map<String, Duration> ttls = new HashMap<>();

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
            ttls.put(runId, ChatEvents.TERMINAL_TTL);
        }

        public Duration ttl(String runId) {
            return ttls.get(runId);
        }

        public void bind(String runId, String userId) {
            owners.put(runId, userId);
        }

        public Optional<String> owner(String runId) {
            return Optional.ofNullable(owners.get(runId));
        }
    }

    /**
     * 终态判断仍走真实事件服务，流本身换成内存。
     */
    private static final class MemoryEvents extends ChatEventServiceImpl {
        private final MemoryEventLog events;

        /**
         * 绑定内存事件流。
         *
         * @param events 内存事件
         */
        private MemoryEvents(MemoryEventLog events) {
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
         * 给内存流记下终态过期时间。
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

    private static final class MemoryConversations {
        private final Map<String, Conversation> conversations = new HashMap<>();
        private final Map<String, String> runs = new HashMap<>();

        public Conversation create(String userId, String title) {
            Conversation conversation = new Conversation();
            conversation.setId(Ids.ulid());
            conversation.setUserId(userId);
            conversation.setTitle(title);
            conversations.put(conversation.getId(), conversation);
            return conversation;
        }

        public Optional<Conversation> findOwned(String conversationId, String userId) {
            Conversation conversation = conversations.get(conversationId);
            if (conversation == null || !userId.equals(conversation.getUserId())) {
                return Optional.empty();
            }
            return Optional.of(conversation);
        }

        public void insertUserMessage(String conversationId, String clientMsgId, String text, String runId) {
            runs.put(conversationId + "\0" + clientMsgId, runId);
        }

        public Optional<String> findRunId(String conversationId, String clientMsgId) {
            return Optional.ofNullable(runs.get(conversationId + "\0" + clientMsgId));
        }
    }

    /**
     * 开 run 仍走真实受理，会话表换成内存。
     */
    private static final class MemoryTurns extends ChatTurnServiceImpl {
        private final MemoryConversations conversations;

        /**
         * 绑定内存会话。积分门默认放行。
         *
         * @param conversations 内存会话
         * @param cancels 取消与属主
         * @param worker 工人
         * @param executor 工人线程
         * @param clock 截止时间
         * @param timeout 这一轮允许跑多久
         */
        private MemoryTurns(
                MemoryConversations conversations,
                IChatCancelService cancels,
                IChatWorker worker,
                Executor executor,
                Clock clock,
                Duration timeout) {
            super(null, null, null, cancels, worker, executor, clock, timeout);
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
}
