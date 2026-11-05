package com.omino.shopai.app.service.chat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 一次生成的标识、输入和截止时间。
 */
public record ChatRun(
        String runId,
        String userId,
        String conversationId,
        String clientMsgId,
        String inputType,
        String text,
        String sourceUrl,
        String imageObjectKey,
        Map<String, Object> hitl,
        List<String> installedPlatforms,
        Instant deadline) {

    /**
     * 只有文本的一轮。测试和旧入口用它补上其余空字段。
     *
     * @param runId 这一轮的编号
     * @param userId 用户主键
     * @param conversationId 会话主键
     * @param clientMsgId 客户端消息号
     * @param text 用户文本
     * @param deadline 截止时间
     * @return 一轮生成
     */
    public static ChatRun text(
            String runId,
            String userId,
            String conversationId,
            String clientMsgId,
            String text,
            Instant deadline) {
        return new ChatRun(runId, userId, conversationId, clientMsgId, "text", text, null, null, null, List.of(), deadline);
    }
}
