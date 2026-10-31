package com.omino.shopai.app.web.app.dto;

/**
 * 一轮对话的文本和客户端消息号。
 */
public record TurnRequestDto(String conversationId, String clientMsgId, String inputType, String text, DeviceDto device) {
}
