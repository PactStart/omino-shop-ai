package com.omino.shopai.admin.web.dto;

/**
 * 工单上可以改的三项。空字段表示不改。
 *
 * @param status 状态码
 * @param priority 优先级
 * @param assigneeSsoSub 处理人
 */
public record TicketChangeDto(String status, String priority, String assigneeSsoSub) {
}
