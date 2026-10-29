package com.omino.shopai.app.service.meta;

import org.springframework.stereotype.Service;

import java.util.Map;
/**
 * AppRuntimeConfigService 编排用例，事务和校验放在这里。
 */

@Service
public class AppRuntimeConfigService {

    private final AppReleaseService releases;

    public AppRuntimeConfigService(AppReleaseService releases) {
        this.releases = releases;
    }

    public Map<String, Object> merge(String clientType, String channel, int versionCode) {
        return releases.runtimeConfig(clientType, channel, versionCode);
    }
}
