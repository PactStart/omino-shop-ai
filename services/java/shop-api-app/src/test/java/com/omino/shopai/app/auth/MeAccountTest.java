package com.omino.shopai.app.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.api.ApiExceptionHandler;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.dto.SuggestionViewDto;
import com.omino.shopai.app.security.AppJwtFilter;
import com.omino.shopai.app.service.agent.AgentConfigServiceImpl;
import com.omino.shopai.app.service.auth.AppJwtIssuer;
import com.omino.shopai.app.service.auth.MeQueryService;
import com.omino.shopai.app.service.file.ImageFileService;
import com.omino.shopai.app.web.app.MeController;
import com.omino.shopai.entity.AppUser;
import com.omino.shopai.entity.ChatSuggestion;
import com.omino.shopai.enums.ActiveStatus;
import com.omino.shopai.enums.OnOff;
import com.omino.shopai.enums.UserStatus;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.ConversationMessageMapper;
import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeAccountTest {

    @Test
    void profileMasksPhoneAndPushRoundTrips() {
        AppUser user = user();
        MemoryMe me = new MemoryMe(user);
        var view = me.get(user.getId());
        assertEquals("138****8000", view.phone());
        assertTrue(view.pushEnabled());
        me.updatePush(user.getId(), false);
        assertEquals(OnOff.OFF, user.getPushEnabled());
        assertFalse(me.get(user.getId()).pushEnabled());
    }

    @Test
    void deleteRequiresTheWordAndRefusesGodView() {
        AppUser user = user();
        MemoryMe me = new MemoryMe(user);
        ApiBusinessException wrong = assertThrows(ApiBusinessException.class, () -> me.delete(user.getId(), "delete", false));
        assertEquals(ErrorCodes.VALIDATION, wrong.code());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        ApiBusinessException god = assertThrows(ApiBusinessException.class, () -> me.delete(user.getId(), "DELETE", true));
        assertEquals(ErrorCodes.FORBIDDEN, god.code());
        me.delete(user.getId(), "DELETE", false);
        assertEquals(UserStatus.DISABLED, user.getStatus());
        assertTrue(user.getDeletedAt() != null);
        assertThrows(ApiBusinessException.class, () -> me.get(user.getId()));
    }

    @Test
    void deletedTokenIsRejected() throws Exception {
        AppJwtIssuer tokens = new AppJwtIssuer("0123456789abcdef0123456789abcdef", 1800);
        String token = tokens.issue("01USER0000000000000000000A");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MeController(new MemoryMe(user())))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new AppJwtFilter(tokens, new ObjectMapper(), id -> false))
                .build();
        mvc.perform(get("/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void suggestionsSkipRetiredAndSort() {
        ChatSuggestion later = suggestion("b", "后一句", 2, ActiveStatus.ACTIVE);
        ChatSuggestion first = suggestion("a", "先一句", 1, ActiveStatus.ACTIVE);
        ChatSuggestion hidden = suggestion("c", "下架", 0, ActiveStatus.RETIRED);
        MemorySuggestions suggestions = new MemorySuggestions(List.of(later, hidden, first));
        List<SuggestionViewDto> active = suggestions.active();
        assertEquals(List.of("a", "b"), active.stream().map(SuggestionViewDto::id).toList());
        assertEquals("先一句", active.get(0).text());
    }

    @Test
    void imageUploadKeepsJpegAndRejectsText(@TempDir Path dir) throws Exception {
        ImageFileService files = new ImageFileService(dir.toString());
        String key = files.store("01USER0000000000000000000A", new MockMultipartFile(
                "file", "shot.jpg", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, 1, 2}));
        assertTrue(key.endsWith(".jpg"));
        assertTrue(Files.exists(dir.resolve(key)));
        ApiBusinessException rejected = assertThrows(ApiBusinessException.class, () -> files.store(
                "01USER0000000000000000000A",
                new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes())));
        assertEquals(ErrorCodes.VALIDATION, rejected.code());
    }

    @Test
    void messageInsertCastsPayload() throws Exception {
        Insert insert = ConversationMessageMapper.class
                .getMethod("insertMessage", com.omino.shopai.entity.ConversationMessage.class)
                .getAnnotation(Insert.class);
        assertTrue(String.join(" ", insert.value()).contains("CAST(#{payload} AS jsonb)"));
    }

    private static AppUser user() {
        AppUser user = new AppUser();
        user.setId("01USER0000000000000000000A");
        user.setNickname("用户");
        user.setPhone("13800138000");
        user.setInviteCode("OMINO8K2");
        user.setLocale("zh-CN");
        user.setStatus(UserStatus.ACTIVE);
        user.setPushEnabled(OnOff.ON);
        user.setCreatedAt(OffsetDateTime.of(2026, 2, 18, 0, 0, 0, 0, ZoneOffset.UTC));
        return user;
    }

    private static ChatSuggestion suggestion(String id, String text, int sort, ActiveStatus status) {
        ChatSuggestion row = new ChatSuggestion();
        row.setId(id);
        row.setText(text);
        row.setSort(sort);
        row.setStatus(status);
        return row;
    }

    private static final class MemoryMe extends MeQueryService {
        private AppUser user;

        private MemoryMe(AppUser user) {
            super(null);
            this.user = user;
        }

        @Override
        protected AppUser load(String userId) {
            return user != null && user.getId().equals(userId) ? user : null;
        }

        @Override
        protected void save(AppUser user) {
            this.user = user;
        }
    }

    private static final class MemorySuggestions extends AgentConfigServiceImpl {
        private final List<ChatSuggestion> rows;

        private MemorySuggestions(List<ChatSuggestion> rows) {
            super(null, Clock.systemUTC());
            this.rows = rows;
        }

        @Override
        protected List<ChatSuggestion> loadAll() {
            return rows;
        }
    }
}
