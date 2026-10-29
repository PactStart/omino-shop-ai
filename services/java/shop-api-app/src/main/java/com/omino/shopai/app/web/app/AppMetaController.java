package com.omino.shopai.app.web.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.service.meta.AppReleaseService;
import com.omino.shopai.app.service.meta.AppRuntimeConfigService;
import com.omino.shopai.entity.Announcement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
/**
 * AppMetaController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "AppMetaController")
@RequestMapping("/v1")
public class AppMetaController {

    private final AppReleaseService releases;
    private final AppRuntimeConfigService configs;
/**
 * 版本、配置、公告和协议地址。控制器不拼渠道逻辑。
 */

    public AppMetaController(AppReleaseService releases, AppRuntimeConfigService configs) {
        this.releases = releases;
        this.configs = configs;
    }

    @Operation(summary = "查询 /app/check-update")
    @GetMapping("/app/check-update")
    public ApiResponse<Map<String, Object>> checkUpdate(
            @RequestHeader("X-Client-Type") String clientType,
            @RequestHeader("X-App-Channel") String channel,
            @RequestHeader("X-App-Version-Code") int versionCode) {
        return ApiResponse.ok(releases.checkUpdate(clientType, channel, versionCode), trace(), now());
    }

    @Operation(summary = "查询 /app/runtime-config")
    @GetMapping("/app/runtime-config")
    public ApiResponse<Map<String, Object>> runtimeConfig(
            @RequestHeader("X-Client-Type") String clientType,
            @RequestHeader("X-App-Channel") String channel,
            @RequestHeader("X-App-Version-Code") int versionCode) {
        return ApiResponse.ok(configs.merge(clientType, channel, versionCode), trace(), now());
    }

    @Operation(summary = "查询 /announcements")
    @GetMapping("/announcements")
    public ApiResponse<List<Announcement>> announcements(
            @RequestHeader("X-Client-Type") String clientType,
            @RequestHeader("X-App-Channel") String channel,
            @RequestHeader("X-App-Version-Code") int versionCode) {
        return ApiResponse.ok(releases.announcements(clientType, channel, versionCode), trace(), now());
    }

    @Operation(summary = "查询 /legal")
    @GetMapping("/legal")
    public ApiResponse<Map<String, String>> legal() {
        return ApiResponse.ok(releases.legal(), trace(), now());
    }

    private static String trace() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String now() {
        return OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString();
    }
}
