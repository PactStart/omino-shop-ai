package com.omino.shopai.app.chat;

import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.app.service.agent.AgentConfigServiceImpl;
import com.omino.shopai.app.service.chat.IAgentGateway;
import com.omino.shopai.app.service.chat.ChatEventServiceImpl;
import com.omino.shopai.app.service.chat.ChatEvents;
import com.omino.shopai.app.service.chat.ChatRun;
import com.omino.shopai.app.service.chat.ChatWorkerImpl;
import com.omino.shopai.app.service.chat.RunContext;
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
        MemoryLog store = new MemoryLog();
        MemoryEvents events = new MemoryEvents(store);
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
        MemoryLog store = new MemoryLog();
        MemoryEvents events = new MemoryEvents(store);
        ChatWorkerImpl worker = new ChatWorkerImpl(events, gateway, Clock.systemUTC(), 30, run -> null);
        ChatRun run = run();
        worker.start(run);
        assertEquals(0, gateway.calls);
        assertTrue(events.after(run.runId(), null).get(0).data().contains("50020"));
    }

    @Test
    void localWithoutAModelMayOmitInjection() {
        CapturingGateway gateway = new CapturingGateway();
        ChatWorkerImpl worker = new ChatWorkerImpl(new MemoryEvents(new MemoryLog()), gateway, Clock.systemUTC(), 30, run -> new RunContext(prompts(), null));
        worker.start(run());
        assertEquals(1, gateway.calls);
        assertNull(gateway.context.llm());
    }

    @Test
    void moreThanTwelveActiveSuggestionsAreRejected() {
        MemoryCatalog catalog = new MemoryCatalog();
        AgentConfigServiceImpl configs = new MemoryConfigs(catalog, Clock.systemUTC());
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

    private static final class MemoryCatalog {
        private int active;

        public Map<String, String> prompts() {
            return Map.of();
        }

        public List<LlmModel> models() {
            return List.of();
        }

        public int activeSuggestionCount() {
            return active;
        }

        public void addSuggestion(ChatSuggestion suggestion) {
            active++;
        }
    }

    /**
     * 条数上限仍走真实添加，计数和写入换成内存。
     */
    private static final class MemoryConfigs extends AgentConfigServiceImpl {
        private final MemoryCatalog catalog;

        /**
         * 绑定内存目录。
         *
         * @param catalog 内存目录
         * @param clock 创建时间
         */
        private MemoryConfigs(MemoryCatalog catalog, Clock clock) {
            super(null, clock);
            this.catalog = catalog;
        }

        /**
         * 读取内存里的生效条数。
         *
         * @return 生效条数
         */
        @Override
        protected int activeSuggestionCount() {
            return catalog.activeSuggestionCount();
        }

        /**
         * 把建议记进内存目录。
         *
         * @param suggestion 推荐问题
         */
        @Override
        protected void insertSuggestion(ChatSuggestion suggestion) {
            catalog.addSuggestion(suggestion);
        }
    }

    private static final class MemoryLog {
        private final Map<String, List<ChatEvents.Event>> streams = new LinkedHashMap<>();
        private final Map<String, Integer> sequences = new LinkedHashMap<>();

        public ChatEvents.Event append(String runId, String event, String data) {
            ChatEvents.check(event);
            int next = sequences.merge(runId, 1, Integer::sum);
            ChatEvents.Event item = new ChatEvents.Event(Integer.toString(next), event, data);
            streams.computeIfAbsent(runId, key -> new ArrayList<>()).add(item);
            return item;
        }

        public List<ChatEvents.Event> after(String runId, String afterId) {
            return streams.getOrDefault(runId, List.of());
        }

        public void markCancel(String runId) {
        }

        public boolean cancelled(String runId) {
            return false;
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
        }

        public java.util.Optional<String> owner(String runId) {
            return java.util.Optional.empty();
        }
    }

    /**
     * 终态判断仍走真实事件服务，流本身换成内存。
     */
    private static final class MemoryEvents extends ChatEventServiceImpl {
        private final MemoryLog events;

        /**
         * 绑定内存事件流。
         *
         * @param events 内存事件
         */
        private MemoryEvents(MemoryLog events) {
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
         * @return 已写入的事件
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
        protected java.util.Optional<String> readOwner(String runId) {
            return events.owner(runId);
        }
    }
}
