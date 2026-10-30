package com.omino.shopai.app.dto;
/**
 * 已受理的一轮。重复的 clientMsgId 返回原来的 runId。
 */

public record TurnAcceptedDto(String conversationId, String runId, String clientMsgId) {
}
