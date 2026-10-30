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

import java.util.List;
import java.util.Map;
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
    /**
     * 按端和渠道检查是否有新版本。
     *
     * @param clientType 客户端类型
     * @param channel 渠道
     * @param versionCode 当前版本号
     * @return 更新信息信封
     */
    @Operation(summary = "检查是否有新版本")
    @GetMapping("/app/check-update")
    public ApiResponse<Map<String, Object>> checkUpdate(
            @RequestHeader("X-Client-Type") String clientType,
            @RequestHeader("X-App-Channel") String channel,
            @RequestHeader("X-App-Version-Code") int versionCode) {
        return ApiResponse.ok(releases.checkUpdate(clientType, channel, versionCode));
    }
    /**
     * 读取当前端和渠道的运行时配置。
     *
     * @param clientType 客户端类型
     * @param channel 渠道
     * @param versionCode 当前版本号
     * @return 配置信封
     */
    @Operation(summary = "读取运行时配置")
    @GetMapping("/app/runtime-config")
    public ApiResponse<Map<String, Object>> runtimeConfig(
            @RequestHeader("X-Client-Type") String clientType,
            @RequestHeader("X-App-Channel") String channel,
            @RequestHeader("X-App-Version-Code") int versionCode) {
        return ApiResponse.ok(configs.merge(clientType, channel, versionCode));
    }
    /**
     * 列出当前端可见的公告。
     *
     * @param clientType 客户端类型
     * @param channel 渠道
     * @return 公告信封
     */
    @Operation(summary = "列出当前公告")
    @GetMapping("/announcements")
    public ApiResponse<List<Announcement>> announcements(
            @RequestHeader("X-Client-Type") String clientType,
            @RequestHeader("X-App-Channel") String channel,
            @RequestHeader("X-App-Version-Code") int versionCode) {
        return ApiResponse.ok(releases.announcements(clientType, channel, versionCode));
    }
    /**
     * 读取用户协议和隐私政策地址。
     *
     * @return 协议地址信封
     */
    @Operation(summary = "读取协议地址")
    @GetMapping("/legal")
    public ApiResponse<Map<String, String>> legal() {
        return ApiResponse.ok(releases.legal());
    }


}
