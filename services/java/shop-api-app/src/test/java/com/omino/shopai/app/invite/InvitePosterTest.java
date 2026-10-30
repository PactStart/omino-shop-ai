package com.omino.shopai.app.invite;

import com.omino.shopai.enums.UserStatus;
import com.omino.shopai.enums.ActiveStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.api.ApiExceptionHandler;
import com.omino.shopai.app.service.invite.InviteCatalog;
import com.omino.shopai.app.service.invite.InvitePosterService;
import com.omino.shopai.app.web.app.InviteController;
import com.omino.shopai.entity.AppUser;
import com.omino.shopai.entity.InvitePosterTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvitePosterTest {

    @Test
    void activePostersKeepTheRectangleAndLookupHidesProfile() throws Exception {
        MemoryCatalog catalog = new MemoryCatalog();
        catalog.users.add(user("01USER0000000000000000000A", "ABCD2345", "小明", "13800138000"));
        catalog.posters.add(poster("01POST0000000000000000000A", "active", "https://img.example/a.png", 1, null, null));
        catalog.posters.add(poster("01POST0000000000000000000B", "retired", "https://img.example/b.png", 2, null, null));
        catalog.posters.add(poster("01POST0000000000000000000C", "active", "https://img.example/c.png", 3, null,
                OffsetDateTime.ofInstant(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC)));
        InvitePosterService service = new InvitePosterService(
                catalog,
                Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC),
                "https://land.example");
        List<InvitePosterService.PosterView> posters = service.posters();
        assertEquals(1, posters.size());
        assertEquals(12, posters.get(0).qrWidth());
        String json = new ObjectMapper().writeValueAsString(posters);
        assertFalse(json.contains("rendered"));
        assertFalse(json.contains("composited"));
        var mvc = MockMvcBuilders.standaloneSetup(new InviteController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/v1/invite/lookup").param("code", "ABCD2345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.nickname").doesNotExist())
                .andExpect(jsonPath("$.data.phone").doesNotExist());
        mvc.perform(get("/v1/invite/lookup").param("code", "MISSING1"))
                .andExpect(jsonPath("$.code").value(40400));
    }

    private static AppUser user(String id, String code, String nickname, String phone) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setInviteCode(code);
        user.setNickname(nickname);
        user.setPhone(phone);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private static InvitePosterTemplate poster(String id, String status, String image, int sort, OffsetDateTime starts, OffsetDateTime ends) {
        InvitePosterTemplate row = new InvitePosterTemplate();
        row.setId(id);
        row.setName(id);
        row.setImageObjectKey(image);
        row.setCanvasWidth(750);
        row.setCanvasHeight(1334);
        row.setQrX(40);
        row.setQrY(40);
        row.setQrWidth(12);
        row.setQrHeight(12);
        row.setSort(sort);
        row.setStatus(ActiveStatus.fromCode(status));
        row.setStartsAt(starts);
        row.setEndsAt(ends);
        return row;
    }

    private static final class MemoryCatalog implements InviteCatalog {
        private final List<InvitePosterTemplate> posters = new ArrayList<>();
        private final List<AppUser> users = new ArrayList<>();

        @Override
        public List<InvitePosterTemplate> posters() {
            return posters;
        }

        @Override
        public Optional<AppUser> findById(String id) {
            return users.stream().filter(user -> id.equals(user.getId())).findFirst();
        }

        @Override
        public Optional<AppUser> findByInviteCode(String code) {
            return users.stream().filter(user -> code.equals(user.getInviteCode())).findFirst();
        }
    }
}
