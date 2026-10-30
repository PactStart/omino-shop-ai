package com.omino.shopai.app.service.chat;

import java.time.Instant;
/**
 * 一次生成的标识、用户和截止时间。
 */

public record ChatRun(
        String runId,
        String userId,
        String conversationId,
        String clientMsgId,
        String text,
        Instant deadline) {
}
