package com.omino.shopai.app.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.api.ApiExceptionHandler;
import com.omino.shopai.app.service.chat.AgentGateway;
import com.omino.shopai.app.service.chat.AgentUnavailableException;
import com.omino.shopai.app.service.chat.ChatCancelService;
import com.omino.shopai.app.service.chat.ChatEventPump;
import com.omino.shopai.app.service.chat.ChatEvents;
import com.omino.shopai.app.service.chat.ChatRun;
import com.omino.shopai.app.service.chat.ChatRunWorker;
import com.omino.shopai.app.service.chat.ChatTurnService;
import com.omino.shopai.app.service.chat.ConversationStore;
import com.omino.shopai.app.service.chat.FixtureChatWorker;
import com.omino.shopai.app.service.chat.HttpAgentGateway;
import com.omino.shopai.app.service.chat.RunEventLog;
import com.omino.shopai.app.service.chat.TurnAccepted;
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

    private MemoryEventLog events;
    private MemoryConversations conversations;
    private ScriptedGateway gateway;
    private QueuedExecutor executor;
    private MutableClock clock;
    private ChatCancelService cancels;
    private ChatTurnService turns;
    private ChatEventPump pump;
    private ChatRunWorker worker;

    @BeforeEach
    void setUp() {
        events = new MemoryEventLog();
        conversations = new MemoryConversations();
        gateway = new ScriptedGateway();
        executor = new QueuedExecutor();
        clock = new MutableClock(Instant.parse("2026-10-29T14:00:00Z"));
        cancels = new ChatCancelService(events);
        worker = new ChatRunWorker(events, gateway, clock, 30);
        turns = new ChatTurnService(conversations, cancels, worker, executor, clock, Duration.ofSeconds(30));
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
        assertTrue(events.streams.isEmpty());
    }

    @Test
    void sameClientMessageReturnsTheSameRun() {
        TurnAccepted first = turns.open(USER, null, "c1", "找耳机");
        TurnAccepted second = turns.open(USER, first.conversationId(), "c1", "找耳机");
        assertEquals(first.runId(), second.runId());
        assertEquals(1, executor.pending());
    }

    @Test
    void disconnectKeepsEventsAndDoesNotCancel() {
        gateway.script = sink -> {
            sink.event("text_delta", "{\"text\":\"你好\"}");
            sink.event("done", "{\"conversationId\":\"c\",\"serverMsgId\":\"m\",\"intent\":\"shop.recommend\"}");
        };
        TurnAccepted accepted = turns.open(USER, null, "c1", "找耳机");
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
        TurnAccepted accepted = turns.open(USER, null, "c1", "找耳机");
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
        TurnAccepted accepted = turns.open(USER, null, "c1", "找耳机");
        executor.drain();
        List<ChatEvents.Event> written = events.after(accepted.runId(), null);
        assertEquals(List.of("error"), written.stream().map(ChatEvents.Event::event).toList());
        JsonNode data = JSON.readTree(written.get(0).data());
        assertEquals(50020, data.get("code").asInt());
        assertEquals(ChatEvents.TERMINAL_TTL, events.ttl(accepted.runId()));
    }

    @Test
    void expiredRunWritesTimeoutWithoutCallingAgent() {
        TurnAccepted accepted = turns.open(USER, null, "c1", "找耳机");
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
        assertEquals(List.of("local"), List.of(FixtureChatWorker.class.getAnnotation(Profile.class).value()));
        assertFalse(List.of(ChatRunWorker.class.getAnnotation(Profile.class).value()).contains("local"));
        assertTrue(List.of(ChatRunWorker.class.getAnnotation(Profile.class).value()).containsAll(List.of("test", "prod")));
        assertFalse(List.of(HttpAgentGateway.class.getAnnotation(Profile.class).value()).contains("local"));
    }

    @Test
    void unknownEventNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> events.append("run", "sample_done", "{}"));
    }

    private static final class ScriptedGateway implements AgentGateway {
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

    private static final class MemoryEventLog implements RunEventLog {
        private final Map<String, List<ChatEvents.Event>> streams = new HashMap<>();
        private final Map<String, Integer> sequences = new HashMap<>();
        private final Set<String> cancels = new HashSet<>();
        private final Map<String, String> owners = new HashMap<>();
        private final Map<String, Duration> ttls = new HashMap<>();

        @Override
        public ChatEvents.Event append(String runId, String event, String data) {
            ChatEvents.check(event);
            int next = sequences.merge(runId, 1, Integer::sum);
            ChatEvents.Event item = new ChatEvents.Event(Integer.toString(next), event, data);
            streams.computeIfAbsent(runId, key -> new ArrayList<>()).add(item);
            return item;
        }

        @Override
        public List<ChatEvents.Event> after(String runId, String afterId) {
            long after = afterId == null || afterId.isBlank() ? 0 : Long.parseLong(afterId);
            return streams.getOrDefault(runId, List.of()).stream()
                    .filter(event -> Long.parseLong(event.id()) > after)
                    .toList();
        }

        @Override
        public void markCancel(String runId) {
            cancels.add(runId);
        }

        @Override
        public boolean cancelled(String runId) {
            return cancels.contains(runId);
        }

        @Override
        public boolean terminal(String runId) {
            return after(runId, null).stream().anyMatch(event -> ChatEvents.TERMINAL.contains(event.event()));
        }

        @Override
        public void expire(String runId) {
            ttls.put(runId, ChatEvents.TERMINAL_TTL);
        }

        @Override
        public Duration ttl(String runId) {
            return ttls.get(runId);
        }

        @Override
        public void bind(String runId, String userId) {
            owners.put(runId, userId);
        }

        @Override
        public Optional<String> owner(String runId) {
            return Optional.ofNullable(owners.get(runId));
        }
    }

    private static final class MemoryConversations implements ConversationStore {
        private final Map<String, Conversation> conversations = new HashMap<>();
        private final Map<String, String> runs = new HashMap<>();

        @Override
        public Conversation create(String userId, String title) {
            Conversation conversation = new Conversation();
            conversation.setId(Ids.ulid());
            conversation.setUserId(userId);
            conversation.setTitle(title);
            conversations.put(conversation.getId(), conversation);
            return conversation;
        }

        @Override
        public Optional<Conversation> findOwned(String conversationId, String userId) {
            Conversation conversation = conversations.get(conversationId);
            if (conversation == null || !userId.equals(conversation.getUserId())) {
                return Optional.empty();
            }
            return Optional.of(conversation);
        }

        @Override
        public void insertUserMessage(String conversationId, String clientMsgId, String text, String runId) {
            runs.put(conversationId + "\0" + clientMsgId, runId);
        }

        @Override
        public Optional<String> findRunId(String conversationId, String clientMsgId) {
            return Optional.ofNullable(runs.get(conversationId + "\0" + clientMsgId));
        }
    }
}
