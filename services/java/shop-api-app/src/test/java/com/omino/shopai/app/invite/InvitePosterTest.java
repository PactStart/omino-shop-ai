package com.omino.shopai.app.invite;

import com.omino.shopai.enums.UserStatus;
import com.omino.shopai.enums.ActiveStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.api.ApiExceptionHandler;
import com.omino.shopai.app.dto.PosterViewDto;
import com.omino.shopai.app.service.invite.IInvitePosterService;
import com.omino.shopai.app.service.invite.InvitePosterServiceImpl;
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
        IInvitePosterService service = new MemoryInvitePosterService(
                catalog,
                Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC),
                "https://land.example");
        List<PosterViewDto> posters = service.posters();
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

    /**
     * 内存里的海报模板和用户。只提供读取，不筛选是否生效。
     */
    private static final class MemoryCatalog {
        private final List<InvitePosterTemplate> posters = new ArrayList<>();
        private final List<AppUser> users = new ArrayList<>();

        /**
         * 读出内存中的全部模板。
         *
         * @return 海报模板
         */
        public List<InvitePosterTemplate> posters() {
            return posters;
        }

        /**
         * 按主键读取内存中的用户。
         *
         * @param id 用户主键
         * @return 用户；没有则空
         */
        public Optional<AppUser> findById(String id) {
            return users.stream().filter(user -> id.equals(user.getId())).findFirst();
        }

        /**
         * 按邀请码读取内存中的用户。
         *
         * @param code 邀请码
         * @return 用户；没有则空
         */
        public Optional<AppUser> findByInviteCode(String code) {
            return users.stream().filter(user -> code.equals(user.getInviteCode())).findFirst();
        }
    }

    /**
     * 用内存目录替换表访问。生效判断和贴码矩形仍走真实实现。
     */
    private static final class MemoryInvitePosterService extends InvitePosterServiceImpl {

        private final MemoryCatalog catalog;

        /**
         * 绑定同一份内存目录。表访问全部转给它。
         *
         * @param catalog 内存目录
         * @param clock 判断模板是否生效的时钟
         * @param landBase 落地页根地址
         */
        private MemoryInvitePosterService(MemoryCatalog catalog, Clock clock, String landBase) {
            super(null, null, clock, landBase);
            this.catalog = catalog;
        }

        /**
         * 从内存目录读出模板。
         *
         * @return 海报模板
         */
        @Override
        protected List<InvitePosterTemplate> loadPosters() {
            return catalog.posters();
        }

        /**
         * 从内存目录按主键读取用户。
         *
         * @param id 用户主键
         * @return 用户；没有则空
         */
        @Override
        protected Optional<AppUser> findById(String id) {
            return catalog.findById(id);
        }

        /**
         * 从内存目录按邀请码读取用户。
         *
         * @param code 邀请码
         * @return 用户；没有则空
         */
        @Override
        protected Optional<AppUser> findByInviteCode(String code) {
            return catalog.findByInviteCode(code);
        }
    }
}
