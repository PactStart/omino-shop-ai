package com.omino.shopai.app.credit;

import com.omino.shopai.api.ApiExceptionHandler;
import com.omino.shopai.app.adapter.pay.AlipayNotifyVerifier;
import com.omino.shopai.app.adapter.pay.WechatPayNotifyVerifier;
import com.omino.shopai.app.service.chat.ChatCancelService;
import com.omino.shopai.app.service.chat.ChatEvents;
import com.omino.shopai.app.service.chat.ChatTurnService;
import com.omino.shopai.app.service.chat.ConversationStore;
import com.omino.shopai.app.service.chat.RunEventLog;
import com.omino.shopai.app.service.chat.TurnAccepted;
import com.omino.shopai.app.service.credit.CreditBillingService;
import com.omino.shopai.app.service.credit.CreditPurchaseService;
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
        MemoryEvents events = new MemoryEvents();
        MemoryCredits books = new MemoryCredits();
        CreditBillingService billing = new CreditBillingService(books, events, true, "1000");
        QueuedExecutor executor = new QueuedExecutor();
        AtomicInteger workerCalls = new AtomicInteger();
        ChatTurnService turns = new ChatTurnService(
                new MemoryConversations(),
                new ChatCancelService(events),
                run -> workerCalls.incrementAndGet(),
                executor,
                Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneId.of("UTC")),
                Duration.ofSeconds(30),
                billing);
        TurnAccepted accepted = turns.open("01USER0000000000000000000A", null, "msg-1", "想买鞋");
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
        CreditPurchaseService purchases = new CreditPurchaseService(books, Clock.systemUTC());
        CreditBillingService billing = new CreditBillingService(books, new MemoryEvents(), false, "1000");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CreditController(
                        purchases,
                        billing,
                        books,
                        new WechatPayNotifyVerifier("", "", ""),
                        new AlipayNotifyVerifier("")))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        String withdraw = mvc.perform(post("/v1/credits/withdraw").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString();
        String convert = mvc.perform(post("/v1/credits/to-wallet").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(withdraw.contains("\"code\":0"));
        assertFalse(convert.contains("\"code\":0"));
    }

    private static final class MemoryConversations implements ConversationStore {
        @Override
        public Conversation create(String userId, String title) {
            Conversation conversation = new Conversation();
            conversation.setId(Ids.ulid());
            conversation.setUserId(userId);
            conversation.setTitle(title);
            return conversation;
        }

        @Override
        public Optional<Conversation> findOwned(String conversationId, String userId) {
            return Optional.empty();
        }

        @Override
        public void insertUserMessage(String conversationId, String clientMsgId, String text, String runId) {
        }

        @Override
        public Optional<String> findRunId(String conversationId, String clientMsgId) {
            return Optional.empty();
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

    private static final class MemoryEvents implements RunEventLog {
        private final Map<String, List<ChatEvents.Event>> streams = new HashMap<>();
        private final Map<String, Integer> sequences = new HashMap<>();
        private final Set<String> cancels = new HashSet<>();
        private final Map<String, String> owners = new HashMap<>();

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
        }

        @Override
        public Duration ttl(String runId) {
            return null;
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
}
