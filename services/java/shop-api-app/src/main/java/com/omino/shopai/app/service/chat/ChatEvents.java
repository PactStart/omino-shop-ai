package com.omino.shopai.app.service.chat;

import java.time.Duration;
import java.util.List;
import java.util.Set;
/**
 * SSE 写出前的事件形状。
 */

public final class ChatEvents {

    public static final Duration TERMINAL_TTL = Duration.ofMinutes(30);
    public static final Set<String> NAMES = Set.of(
            "intent", "text_delta", "clarify", "product_card", "product_cards", "collection_card",
            "auth_card", "confirm_card", "ticket_created", "usage", "credit_card",
            "error", "done", "cancelled", "credit_exhausted");
    public static final Set<String> TERMINAL = Set.of("done", "error", "cancelled", "credit_exhausted");

    private ChatEvents() {
    }
/**
 * 确认这条事件属于该 run，避免串到别人的流。
 */

    public static void check(String event) {
        if (!NAMES.contains(event)) {
            throw new IllegalArgumentException("未知事件 " + event);
        }
    }
/**
 * 一条已经落进流的对话事件。
 */

    public record Event(String id, String event, String data) {
    }
}
