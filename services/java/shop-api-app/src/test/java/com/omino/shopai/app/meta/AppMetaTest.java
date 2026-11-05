package com.omino.shopai.app.meta;

import com.omino.shopai.enums.PublishStatus;
import com.omino.shopai.enums.ClientType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ApiExceptionHandler;
import com.omino.shopai.app.security.ClientHeaderFilter;
import com.omino.shopai.app.service.meta.AppReleaseServiceImpl;
import com.omino.shopai.app.service.meta.IAppReleaseService;
import com.omino.shopai.app.service.meta.IInboxService;
import com.omino.shopai.app.service.meta.InboxServiceImpl;
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
        IInboxService inbox = new MemoryInbox();
        inbox.send("01USER0000000000000000000A", "你好", "只给你");
        assertEquals(1, inbox.list("01USER0000000000000000000A").size());
        assertTrue(inbox.list("01USER0000000000000000000B").isEmpty());
        IAppReleaseService releases = new MemoryReleases();
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

    /**
     * 用内存列表替换站内信表访问。发送和列表仍走真实用例。
     */
    private static final class MemoryInbox extends InboxServiceImpl {
        private final List<InboxMessage> rows = new ArrayList<>();

        /**
         * 不接数据库。时钟用系统 UTC，和原来的测试相同。
         */
        private MemoryInbox() {
            super(null, Clock.systemUTC());
        }

        /**
         * 把站内信放进内存列表。
         *
         * @param message 站内信
         */
        @Override
        protected void insertInbox(InboxMessage message) {
            rows.add(message);
        }

        /**
         * 内存列表不回写。这条路径本测试不走到。
         *
         * @param message 站内信
         */
        @Override
        protected void saveInbox(InboxMessage message) {
        }

        /**
         * 从内存列表里筛出该用户的站内信。
         *
         * @param userId 接收人
         * @return 该用户的站内信
         */
        @Override
        protected List<InboxMessage> inbox(String userId) {
            return rows.stream().filter(row -> userId.equals(row.getUserId())).toList();
        }

        /**
         * 内存列表不按主键查找。这条路径本测试不走到。
         *
         * @param id 站内信主键
         * @return 空
         */
        @Override
        protected InboxMessage findInbox(String id) {
            return null;
        }
    }

    /**
     * 用内存列表替换版本、配置和公告的表访问。发布校验仍走真实用例。
     */
    private static final class MemoryReleases extends AppReleaseServiceImpl {
        private final List<AppRelease> rows = new ArrayList<>();

        /**
         * 不接数据库。协议地址用测试里原来的占位。
         */
        private MemoryReleases() {
            super(null, null, null, Clock.systemUTC(), "https://a.example", "https://p.example");
        }

        /**
         * 把版本放进内存列表。
         *
         * @param release 版本
         */
        @Override
        protected void insertRelease(AppRelease release) {
            rows.add(release);
        }

        /**
         * 判断内存列表里是否已有同端同渠道同版本号。
         *
         * @param clientType 客户端类型码
         * @param channel 渠道
         * @param versionCode 版本号
         * @return 已有该版本时为真
         */
        @Override
        protected boolean releaseExists(String clientType, String channel, int versionCode) {
            return rows.stream().anyMatch(row -> clientType.equals(row.getClientType() == null ? null : row.getClientType().code())
                    && channel.equals(row.getChannel())
                    && row.getVersionCode() == versionCode);
        }

        /**
         * 本测试不读已发布版本。
         *
         * @param clientType 客户端类型码
         * @param channel 渠道
         * @return 空列表
         */
        @Override
        protected List<AppRelease> publishedReleases(String clientType, String channel) {
            return List.of();
        }

        /**
         * 本测试不读运行时配置。
         *
         * @return 空列表
         */
        @Override
        protected List<AppRuntimeConfig> activeConfigs() {
            return List.of();
        }

        /**
         * 本测试不读公告。
         *
         * @return 空列表
         */
        @Override
        protected List<Announcement> announcements() {
            return List.of();
        }
    }
}
