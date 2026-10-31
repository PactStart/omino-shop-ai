package com.omino.shopai.app.chat;

import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.app.service.agent.IAgentCatalog;
import com.omino.shopai.app.service.agent.AgentConfigService;
import com.omino.shopai.app.service.chat.IAgentGateway;
import com.omino.shopai.app.service.chat.ChatEvents;
import com.omino.shopai.app.service.chat.ChatRun;
import com.omino.shopai.app.service.chat.ChatWorkerImpl;
import com.omino.shopai.app.service.chat.RunContext;
import com.omino.shopai.app.service.chat.IRunEventLog;
import com.omino.shopai.entity.ChatSuggestion;
import com.omino.shopai.entity.LlmModel;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunInjectionTest {

    @Test
    void openingARunInjectsEightPromptsAndOneModel() {
        CapturingGateway gateway = new CapturingGateway();
        MemoryLog events = new MemoryLog();
        RunContext ready = new RunContext(prompts(), new RunContext.LlmChoice("m1", "https://api.deepseek.com", "deepseek-chat", "secret-value-1234", 30000));
        ChatWorkerImpl worker = new ChatWorkerImpl(events, gateway, Clock.systemUTC(), 30, run -> ready);
        worker.start(run());
        assertEquals(1, gateway.calls);
        assertEquals(8, gateway.context.prompts().size());
        assertEquals("deepseek-chat", gateway.context.llm().modelName());
    }

    @Test
    void missingPromptDoesNotCallAgent() {
        CapturingGateway gateway = new CapturingGateway();
        MemoryLog events = new MemoryLog();
        ChatWorkerImpl worker = new ChatWorkerImpl(events, gateway, Clock.systemUTC(), 30, run -> null);
        ChatRun run = run();
        worker.start(run);
        assertEquals(0, gateway.calls);
        assertTrue(events.after(run.runId(), null).get(0).data().contains("50020"));
    }

    @Test
    void localWithoutAModelMayOmitInjection() {
        CapturingGateway gateway = new CapturingGateway();
        ChatWorkerImpl worker = new ChatWorkerImpl(new MemoryLog(), gateway, Clock.systemUTC(), 30, run -> new RunContext(prompts(), null));
        worker.start(run());
        assertEquals(1, gateway.calls);
        assertNull(gateway.context.llm());
    }

    @Test
    void moreThanTwelveActiveSuggestionsAreRejected() {
        MemoryCatalog catalog = new MemoryCatalog();
        AgentConfigService configs = new AgentConfigService(catalog, Clock.systemUTC());
        for (int i = 0; i < 12; i++) {
            configs.addActive("问题" + i);
        }
        assertThrows(ApiBusinessException.class, () -> configs.addActive("第十三条"));
    }

    private static ChatRun run() {
        return new ChatRun("run-1", "01USER0000000000000000000A", "conv", "m1", "你好", Instant.now().plus(Duration.ofMinutes(1)));
    }

    private static Map<String, String> prompts() {
        Map<String, String> rows = new LinkedHashMap<>();
        for (String key : com.omino.shopai.support.AgentPrompts.KEYS) {
            rows.put(key, key + " 正文");
        }
        return rows;
    }

    private static final class CapturingGateway implements IAgentGateway {
        private int calls;
        private RunContext context;

        @Override
        public void stream(ChatRun run, IAgentGateway.AgentSink sink) {
        }

        @Override
        public void stream(ChatRun run, RunContext context, IAgentGateway.AgentSink sink) {
            calls++;
            this.context = context;
            sink.event("done", "{}");
        }
    }

    private static final class MemoryCatalog implements IAgentCatalog {
        private int active;

        @Override
        public Map<String, String> prompts() {
            return Map.of();
        }

        @Override
        public List<LlmModel> models() {
            return List.of();
        }

        @Override
        public int activeSuggestionCount() {
            return active;
        }

        @Override
        public void addSuggestion(ChatSuggestion suggestion) {
            active++;
        }
    }

    private static final class MemoryLog implements IRunEventLog {
        private final Map<String, List<ChatEvents.Event>> streams = new LinkedHashMap<>();
        private final Map<String, Integer> sequences = new LinkedHashMap<>();

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
            return streams.getOrDefault(runId, List.of());
        }

        @Override
        public void markCancel(String runId) {
        }

        @Override
        public boolean cancelled(String runId) {
            return false;
        }

        @Override
        public boolean terminal(String runId) {
            return after(runId, null).stream().anyMatch(event -> ChatEvents.TERMINAL.contains(event.event()));
        }

        @Override
        public void expire(String runId) {
        }

        @Override
        public Duration ttl(String runId) {
            return null;
        }

        @Override
        public void bind(String runId, String userId) {
        }

        @Override
        public java.util.Optional<String> owner(String runId) {
            return java.util.Optional.empty();
        }
    }
}
