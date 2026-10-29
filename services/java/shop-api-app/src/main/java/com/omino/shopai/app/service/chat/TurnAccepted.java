package com.omino.shopai.app.service.chat;
/**
 * 已受理的一轮。重复的 clientMsgId 返回原来的 runId。
 */

public record TurnAccepted(String conversationId, String runId, String clientMsgId) {
}
