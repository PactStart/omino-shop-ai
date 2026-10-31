package com.omino.shopai.app.web.app.dto;

import java.util.List;

/**
 * 开 run 时带上的端信息，用于归因。
 */
public record DeviceDto(
        String os,
        String osVersion,
        String clientType,
        String channel,
        Integer versionCode,
        String versionName,
        List<String> installedPlatforms) {
}
