package com.omino.shopai.app.service.meta;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.Announcement;
import com.omino.shopai.entity.AppRelease;
import com.omino.shopai.entity.AppRuntimeConfig;
import com.omino.shopai.enums.ActiveStatus;
import com.omino.shopai.enums.OnOff;
import com.omino.shopai.enums.PublishStatus;
import com.omino.shopai.enums.RichTextFormat;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.AnnouncementMapper;
import com.omino.shopai.mapper.AppReleaseMapper;
import com.omino.shopai.mapper.AppRuntimeConfigMapper;
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
 * 版本、配置和公告的筛选放在这里。表访问只在受保护方法里。
 */
@Service
public class AppReleaseServiceImpl implements IAppReleaseService {

    private final AppReleaseMapper releases;
    private final AppRuntimeConfigMapper configs;
    private final AnnouncementMapper announcementRows;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();
    private final String agreementUrl;
    private final String privacyUrl;

    /**
     * 注入版本、配置、公告和协议地址。
     *
     * @param releases 版本表
     * @param configs 运行时配置表
     * @param announcementRows 公告表
     * @param clock 发布时间和公告是否在有效期内
     * @param agreementUrl 用户协议地址，可空
     * @param privacyUrl 隐私政策地址，可空
     */
    public AppReleaseServiceImpl(
            AppReleaseMapper releases,
            AppRuntimeConfigMapper configs,
            AnnouncementMapper announcementRows,
            Clock clock,
            @Value("${LEGAL_AGREEMENT_URL:}") String agreementUrl,
            @Value("${LEGAL_PRIVACY_URL:}") String privacyUrl) {
        this.releases = releases;
        this.configs = configs;
        this.announcementRows = announcementRows;
        this.clock = clock;
        this.agreementUrl = agreementUrl;
        this.privacyUrl = privacyUrl;
    }

    /**
     * 发布一个版本。版本号不在日期范围内，或同端同渠道已有该版本号，则拒绝。
     *
     * @param release 待发布的版本
     * @return 已写入的版本
     */
    @Override
    public AppRelease publish(AppRelease release) {
        if (release.getVersionCode() == null || release.getVersionCode() < 20000101 || release.getVersionCode() > 20991231) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "版本号不正确");
        }
        if (releaseExists(release.getClientType() == null ? null : release.getClientType().code(), release.getChannel(), release.getVersionCode())) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "版本已存在");
        }
        if (release.getId() == null) {
            release.setId(Ids.ulid());
        }
        release.setCreatedAt(now());
        release.setUpdatedAt(now());
        insertRelease(release);
        return release;
    }

    /**
     * 按端和渠道检查是否有比当前版本更新的已发布版本。本渠道没有时改用该端的默认渠道。
     *
     * @param clientType 客户端类型
     * @param channel 渠道
     * @param versionCode 当前版本号
     * @return 无更新时只有 action=none；有更新时带版本、说明和下载地址
     */
    @Override
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
        body.put("title", latest.getTitle());
        body.put("changelog", latest.getChangelog());
        body.put("format", latest.getFormat() == null ? RichTextFormat.PLAIN.code() : latest.getFormat().code());
        body.put("downloadUrl", latest.getDownloadUrl());
        return body;
    }

    /**
     * 把命中端、渠道和版本区间的配置按优先级从低到高浅合并。后写入的键盖过先写入的键。
     *
     * @param clientType 客户端类型
     * @param channel 渠道
     * @param versionCode 当前版本号
     * @return 合并后的配置
     */
    @Override
    public Map<String, Object> runtimeConfig(String clientType, String channel, int versionCode) {
        ObjectNode merged = json.createObjectNode();
        activeConfigs().stream()
                .filter(row -> matches(row.getClientType() == null ? null : row.getClientType().code(), clientType)
                        && matches(row.getChannel(), channel))
                .filter(row -> inRange(versionCode, row.getVersionCodeMin(), row.getVersionCodeMax()))
                .sorted(Comparator.comparing(row -> row.getPriority() == null ? 0 : row.getPriority()))
                .forEach(row -> overlay(merged, row.getPayload()));
        return json.convertValue(merged, Map.class);
    }

    /**
     * 按端、渠道、版本和时间筛出当前该看到的已发布公告。
     *
     * @param clientType 客户端类型
     * @param channel 渠道
     * @param versionCode 当前版本号
     * @return 当前可见的公告
     */
    @Override
    public List<Announcement> announcements(String clientType, String channel, int versionCode) {
        OffsetDateTime now = now();
        return announcements().stream()
                .filter(row -> contains(row.getClientTypes(), clientType) && contains(row.getChannels(), channel))
                .filter(row -> inRange(versionCode, row.getVersionCodeMin(), row.getVersionCodeMax()))
                .filter(row -> row.getStartsAt() == null || !now.isBefore(row.getStartsAt()))
                .filter(row -> row.getEndsAt() == null || now.isBefore(row.getEndsAt()))
                .toList();
    }

    /**
     * 读取用户协议和隐私政策地址。地址来自环境，可为空字符串。
     *
     * @return agreementUrl 与 privacyUrl
     */
    @Override
    public Map<String, String> legal() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("agreementUrl", agreementUrl);
        body.put("privacyUrl", privacyUrl);
        return body;
    }

    /**
     * 插入一条版本。主键和时间由调用方填好。
     *
     * @param release 版本
     */
    protected void insertRelease(AppRelease release) {
        releases.insert(release);
    }

    /**
     * 判断该端该渠道是否已有这个版本号，避免重复发布。
     *
     * @param clientType 客户端类型码
     * @param channel 渠道
     * @param versionCode 版本号
     * @return 已有该版本时为真
     */
    protected boolean releaseExists(String clientType, String channel, int versionCode) {
        Long count = releases.selectCount(new LambdaQueryWrapper<AppRelease>()
                .eq(AppRelease::getClientType, clientType)
                .eq(AppRelease::getChannel, channel)
                .eq(AppRelease::getVersionCode, versionCode));
        return count != null && count > 0;
    }

    /**
     * 列出该端该渠道已发布的版本。
     *
     * @param clientType 客户端类型码
     * @param channel 渠道
     * @return 已发布版本
     */
    protected List<AppRelease> publishedReleases(String clientType, String channel) {
        return releases.selectList(new LambdaQueryWrapper<AppRelease>()
                .eq(AppRelease::getClientType, clientType)
                .eq(AppRelease::getChannel, channel)
                .eq(AppRelease::getStatus, PublishStatus.PUBLISHED));
    }

    /**
     * 列出仍生效的运行时配置。
     *
     * @return 生效中的配置
     */
    protected List<AppRuntimeConfig> activeConfigs() {
        return configs.selectList(new LambdaQueryWrapper<AppRuntimeConfig>().eq(AppRuntimeConfig::getStatus, ActiveStatus.ACTIVE));
    }

    /**
     * 列出已发布的公告。端、渠道和时间由调用方再筛。
     *
     * @return 已发布公告
     */
    protected List<Announcement> announcements() {
        return announcementRows.selectList(new LambdaQueryWrapper<Announcement>().eq(Announcement::getStatus, PublishStatus.PUBLISHED));
    }

    /**
     * 取该端该渠道版本号最大的已发布版本。
     *
     * @param clientType 客户端类型码
     * @param channel 渠道
     * @return 最新版本；没有则空
     */
    private AppRelease newest(String clientType, String channel) {
        return publishedReleases(clientType, channel).stream()
                .max(Comparator.comparing(AppRelease::getVersionCode))
                .orElse(null);
    }

    /**
     * 没有更精确的渠道时用该端的默认渠道。
     *
     * @param clientType 客户端类型码
     * @return ios 用 appstore，h5 用 web，其余用 official
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

    /**
     * 配置上的端或渠道为空时视为通配。
     *
     * @param expected 配置上的值，可空
     * @param actual 当前请求的值
     * @return 通配或相等时为真
     */
    private static boolean matches(String expected, String actual) {
        return expected == null || expected.isBlank() || expected.equals(actual);
    }

    /**
     * 判断版本号是否落在闭区间里。上下界为空则该侧不限制。
     *
     * @param version 当前版本号
     * @param min 最小版本，可空
     * @param max 最大版本，可空
     * @return 落在区间内时为真
     */
    private static boolean inRange(int version, Integer min, Integer max) {
        return (min == null || version >= min) && (max == null || version <= max);
    }

    /**
     * 判断 JSON 数组文本是否包含该值。列表为空时视为不限制。
     *
     * @param jsonList JSON 数组文本，可空
     * @param value 要匹配的值
     * @return 不限制或包含该值时为真
     */
    private static boolean contains(String jsonList, String value) {
        return jsonList == null || jsonList.isBlank() || jsonList.contains("\"" + value + "\"");
    }

    /**
     * 渠道配置盖过通配。缺项沿用通配，不整行丢掉。坏配置不覆盖已合并的键。
     *
     * @param merged 已经合并的对象
     * @param payload 本行的 JSON 对象文本
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

    /**
     * 取当前时钟的 UTC 时间。
     *
     * @return 当前时间
     */
    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
