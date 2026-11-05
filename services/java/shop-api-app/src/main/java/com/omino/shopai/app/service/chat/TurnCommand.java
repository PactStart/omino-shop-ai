package com.omino.shopai.app.service.chat;

import java.util.List;
import java.util.Map;

/**
 * 开一轮时已经从请求里取出的字段。
 */
public record TurnCommand(
        String conversationId,
        String clientMsgId,
        String inputType,
        String text,
        String sourceUrl,
        String imageObjectKey,
        Map<String, Object> hitl,
        List<String> installedPlatforms) {
}
