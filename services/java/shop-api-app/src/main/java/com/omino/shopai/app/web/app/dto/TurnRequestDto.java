package com.omino.shopai.app.web.app.dto;

import java.util.List;
import java.util.Map;

/**
 * 一轮对话的输入。文本、链接、图片和恢复动作共用这一条。
 */
public record TurnRequestDto(
        String conversationId,
        String clientMsgId,
        String inputType,
        String text,
        String sourceUrl,
        String imageObjectKey,
        Map<String, Object> hitl,
        List<String> installedPlatforms,
        DeviceDto device) {
}
