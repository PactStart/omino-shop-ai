package com.omino.shopai.app.service.meta;

import com.omino.shopai.enums.OnOff;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.Announcement;
import com.omino.shopai.entity.AppRelease;
import com.omino.shopai.entity.AppRuntimeConfig;
import com.omino.shopai.support.Ids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * AppReleaseService 编排用例，事务和校验放在这里。
 */

@Service
public class AppReleaseService {

    private final MetaBook book;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();
    private final String agreementUrl;
    private final String privacyUrl;

    public AppReleaseService(
            MetaBook book,
            Clock clock,
            @Value("${LEGAL_AGREEMENT_URL:}") String agreementUrl,
            @Value("${LEGAL_PRIVACY_URL:}") String privacyUrl) {
        this.book = book;
        this.clock = clock;
        this.agreementUrl = agreementUrl;
        this.privacyUrl = privacyUrl;
    }
/**
 * 发布。
 */

    public AppRelease publish(AppRelease release) {
        if (release.getVersionCode() == null || release.getVersionCode() < 20000101 || release.getVersionCode() > 20991231) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "版本号不正确");
        }
        if (book.releaseExists(release.getClientType() == null ? null : release.getClientType().code(), release.getChannel(), release.getVersionCode())) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "版本已存在");
        }
        if (release.getId() == null) {
            release.setId(Ids.ulid());
        }
        release.setCreatedAt(now());
        release.setUpdatedAt(now());
        book.insertRelease(release);
        return release;
    }

    public Map<String, Object> checkUpdate(String clientType, String channel, int versionCode) {
        AppRelease latest = newest(clientType, channel);
        if (latest == null) {
            latest = newest(clientType, fallback(clientType));
        }
        if (latest == null || latest.getVersionCode() <= versionCode) {
            return Map.of("action", "none");
        }
        String action = latest.getForceUpdate() != null && latest.getForceUpdate() == OnOff.ON ? "force" : "optional";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", action);
        body.put("versionCode", latest.getVersionCode());
        body.put("versionName", latest.getVersionName());
        body.put("downloadUrl", latest.getDownloadUrl());
        return body;
    }

    public Map<String, Object> runtimeConfig(String clientType, String channel, int versionCode) {
        ObjectNode merged = json.createObjectNode();
        book.activeConfigs().stream()
                .filter(row -> matches(row.getClientType() == null ? null : row.getClientType().code(), clientType)
                        && matches(row.getChannel(), channel))
                .filter(row -> inRange(versionCode, row.getVersionCodeMin(), row.getVersionCodeMax()))
                .sorted(Comparator.comparing(row -> row.getPriority() == null ? 0 : row.getPriority()))
                .forEach(row -> overlay(merged, row.getPayload()));
        return json.convertValue(merged, Map.class);
    }
/**
 * 按端和渠道筛出当前用户该看到的公告。
 */

    public List<Announcement> announcements(String clientType, String channel, int versionCode) {
        OffsetDateTime now = now();
        return book.announcements().stream()
                .filter(row -> contains(row.getClientTypes(), clientType) && contains(row.getChannels(), channel))
                .filter(row -> inRange(versionCode, row.getVersionCodeMin(), row.getVersionCodeMax()))
                .filter(row -> row.getStartsAt() == null || !now.isBefore(row.getStartsAt()))
                .filter(row -> row.getEndsAt() == null || now.isBefore(row.getEndsAt()))
                .toList();
    }

    public Map<String, String> legal() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("agreementUrl", agreementUrl);
        body.put("privacyUrl", privacyUrl);
        return body;
    }

    private AppRelease newest(String clientType, String channel) {
        return book.publishedReleases(clientType, channel).stream()
                .max(Comparator.comparing(AppRelease::getVersionCode))
                .orElse(null);
    }
/**
 * 没有更精确的配置时用通配行。
 */

    private static String fallback(String clientType) {
        if ("ios".equals(clientType)) {
            return "appstore";
        }
        if ("h5".equals(clientType)) {
            return "web";
        }
        return "official";
    }

    private static boolean matches(String expected, String actual) {
        return expected == null || expected.isBlank() || expected.equals(actual);
    }

    private static boolean inRange(int version, Integer min, Integer max) {
        return (min == null || version >= min) && (max == null || version <= max);
    }

    private static boolean contains(String jsonList, String value) {
        return jsonList == null || jsonList.isBlank() || jsonList.contains("\"" + value + "\"");
    }
/**
 * 渠道配置盖过通配。缺项沿用通配，不整行丢掉。
 */

    private void overlay(ObjectNode merged, String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }
        try {
            JsonNode node = json.readTree(payload);
            if (node instanceof ObjectNode object) {
                merged.setAll(object);
            }
        } catch (Exception ignored) {
            // 坏配置不覆盖已合并的键。
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
