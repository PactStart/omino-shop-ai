package com.omino.shopai.admin.ops;

import com.omino.shopai.admin.service.ops.SuggestionRules;
import com.omino.shopai.entity.ChatSuggestion;
import com.omino.shopai.enums.ActiveStatus;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggestionTest {

    @Test
    void listKeepsRetiredAndAThirteenthActiveStaysRetired() {
        List<ChatSuggestion> rows = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            rows.add(row("a" + i, ActiveStatus.ACTIVE));
        }
        ChatSuggestion retired = row("retired", ActiveStatus.RETIRED);
        rows.add(retired);
        assertEquals(13, SuggestionRules.list(rows).size());
        assertTrue(SuggestionRules.list(rows).stream().anyMatch(item -> item.getStatus() == ActiveStatus.RETIRED));

        ChatSuggestion changed = SuggestionRules.patch(rows, "a0", " 新的推荐 ", 4, "active");
        assertEquals("新的推荐", changed.getText());
        assertEquals(4, changed.getSort());
        assertEquals(ActiveStatus.ACTIVE, changed.getStatus());

        ApiBusinessException rejected = assertThrows(ApiBusinessException.class,
                () -> SuggestionRules.patch(rows, "retired", "再上架", 1, "active"));
        assertEquals(ErrorCodes.VALIDATION, rejected.code());
        assertEquals(ActiveStatus.RETIRED, retired.getStatus());
    }

    private static ChatSuggestion row(String id, ActiveStatus status) {
        ChatSuggestion row = new ChatSuggestion();
        row.setId(id);
        row.setText("原来的文案");
        row.setSort(0);
        row.setStatus(status);
        return row;
    }
}
