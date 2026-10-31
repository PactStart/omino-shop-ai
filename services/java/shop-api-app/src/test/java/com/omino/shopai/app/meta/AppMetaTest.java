package com.omino.shopai.app.meta;

import com.omino.shopai.enums.PublishStatus;
import com.omino.shopai.enums.ClientType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ApiExceptionHandler;
import com.omino.shopai.app.security.ClientHeaderFilter;
import com.omino.shopai.app.service.meta.AppReleaseService;
import com.omino.shopai.app.service.meta.InboxService;
import com.omino.shopai.app.service.meta.IMeta;
import com.omino.shopai.entity.Announcement;
import com.omino.shopai.entity.AppRelease;
import com.omino.shopai.entity.AppRuntimeConfig;
import com.omino.shopai.entity.InboxMessage;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppMetaTest {

    @Test
    void missingClientHeadersAreRejectedButLoginHealthAndPayAreNot() throws Exception {
        ClientHeaderFilter filter = new ClientHeaderFilter(new ObjectMapper());
        var mvc = MockMvcBuilders.standaloneSetup(new Probe()).addFilters(filter).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/v1/orders")).andExpect(jsonPath("$.code").value(40001));
        mvc.perform(get("/v1/auth/sms/login")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v1/pay/wechat/notify")).andExpect(status().isOk());
    }

    @Test
    void inboxStaysWithItsUserAndReleaseCannotRepeat() {
        MemoryMeta book = new MemoryMeta();
        InboxService inbox = new InboxService(book, Clock.systemUTC());
        inbox.send("01USER0000000000000000000A", "你好", "只给你");
        assertEquals(1, inbox.list("01USER0000000000000000000A").size());
        assertTrue(inbox.list("01USER0000000000000000000B").isEmpty());
        AppReleaseService releases = new AppReleaseService(book, Clock.systemUTC(), "https://a.example", "https://p.example");
        AppRelease release = new AppRelease();
        release.setClientType(ClientType.ANDROID);
        release.setChannel("official");
        release.setVersionCode(20261029);
        release.setVersionName("1");
        release.setStatus(PublishStatus.PUBLISHED);
        releases.publish(release);
        AppRelease again = new AppRelease();
        again.setClientType(ClientType.ANDROID);
        again.setChannel("official");
        again.setVersionCode(20261029);
        again.setStatus(PublishStatus.PUBLISHED);
        assertThrows(ApiBusinessException.class, () -> releases.publish(again));
    }

    @RestController
    static class Probe {
        @GetMapping({"/v1/orders", "/v1/auth/sms/login", "/actuator/health", "/v1/pay/wechat/notify"})
        Map<String, String> ok() {
            return Map.of("status", "UP");
        }
    }

    private static final class MemoryMeta implements IMeta {
        private final List<AppRelease> releases = new ArrayList<>();
        private final List<InboxMessage> inbox = new ArrayList<>();

        @Override
        public void insertRelease(AppRelease release) {
            releases.add(release);
        }

        @Override
        public boolean releaseExists(String clientType, String channel, int versionCode) {
            return releases.stream().anyMatch(row -> clientType.equals(row.getClientType() == null ? null : row.getClientType().code())
                    && channel.equals(row.getChannel())
                    && row.getVersionCode() == versionCode);
        }

        @Override
        public List<AppRelease> publishedReleases(String clientType, String channel) {
            return List.of();
        }

        @Override
        public List<AppRuntimeConfig> activeConfigs() {
            return List.of();
        }

        @Override
        public List<Announcement> announcements() {
            return List.of();
        }

        @Override
        public void insertInbox(InboxMessage message) {
            inbox.add(message);
        }

        @Override
        public void saveInbox(InboxMessage message) {
        }

        @Override
        public List<InboxMessage> inbox(String userId) {
            return inbox.stream().filter(row -> userId.equals(row.getUserId())).toList();
        }

        @Override
        public InboxMessage findInbox(String id) {
            return null;
        }
    }
}
