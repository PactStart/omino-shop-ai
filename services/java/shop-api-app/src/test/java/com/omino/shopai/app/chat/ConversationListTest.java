package com.omino.shopai.app.chat;

import com.omino.shopai.app.dto.ConversationViewDto;
import com.omino.shopai.app.service.chat.ChatTurnServiceImpl;
import com.omino.shopai.dto.PageDto;
import com.omino.shopai.entity.Conversation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 会话列表只映射当前用户，并把页码收进合法范围。
 */
class ConversationListTest {

    @Test
    void mapsOnlyTheRequestedUserAndClampsThePage() {
        OffsetDateTime older = OffsetDateTime.of(2026, 11, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime newer = OffsetDateTime.of(2026, 11, 8, 1, 0, 0, 0, ZoneOffset.UTC);
        ListingTurns turns = new ListingTurns(List.of(
                row("old", "me", "旧对话", older),
                row("other", "someone", "别人", newer),
                row("new", "me", "新对话", newer)));

        PageDto<ConversationViewDto> page = turns.list("me", 0, 99);

        assertEquals("me", turns.userId);
        assertEquals(1, turns.page);
        assertEquals(20, turns.size);
        assertEquals(2, page.total());
        assertEquals("new", page.list().get(0).id());
        assertEquals("新对话", page.list().get(0).title());
        assertEquals(newer, page.list().get(0).updatedAt());
        assertEquals("old", page.list().get(1).id());
    }

    @Test
    void keepsALaterPageInsideTheClampedSize() {
        ListingTurns turns = new ListingTurns(List.of());

        PageDto<ConversationViewDto> page = turns.list("me", 3, 0);

        assertEquals(3, turns.page);
        assertEquals(1, turns.size);
        assertEquals(3, page.page());
        assertEquals(1, page.size());
        assertEquals(0, page.total());
    }

    private static Conversation row(String id, String userId, String title, OffsetDateTime updatedAt) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setUserId(userId);
        conversation.setTitle(title);
        conversation.setUpdatedAt(updatedAt);
        return conversation;
    }

    /**
     * 用内存行代替会话表，记录真正下发的页码。
     */
    private static final class ListingTurns extends ChatTurnServiceImpl {
        private final List<Conversation> rows;
        private String userId;
        private int page;
        private int size;

        /**
         * 绑定内存会话。开 run 用不到的依赖留空。
         *
         * @param rows 全部用户的会话
         */
        private ListingTurns(List<Conversation> rows) {
            super(null, null, null, null, null, null, null, Duration.ofSeconds(1));
            this.rows = rows;
        }

        /**
         * 按用户过滤，最近更新的在前。
         *
         * @param userId 用户主键
         * @param page 已校正的页码
         * @param size 已校正的页大小
         * @return 当前页
         */
        @Override
        protected List<Conversation> listConversations(String userId, int page, int size) {
            this.userId = userId;
            this.page = page;
            this.size = size;
            return rows.stream()
                    .filter(row -> userId.equals(row.getUserId()))
                    .sorted(Comparator.comparing(Conversation::getUpdatedAt).reversed())
                    .limit(size)
                    .toList();
        }

        /**
         * 数该用户的会话。
         *
         * @param userId 用户主键
         * @return 行数
         */
        @Override
        protected long countConversations(String userId) {
            return rows.stream().filter(row -> userId.equals(row.getUserId())).count();
        }
    }
}
