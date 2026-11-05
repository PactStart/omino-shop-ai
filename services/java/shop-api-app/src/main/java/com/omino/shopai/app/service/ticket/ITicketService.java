package com.omino.shopai.app.service.ticket;

import com.omino.shopai.entity.Ticket;
import com.omino.shopai.entity.TicketLog;

import java.util.List;

/**
 * 用户侧工单：建单、改状态、改优先级、证据、回复、指派和日志。
 */
public interface ITicketService {

    /**
     * 创建工单并追加一条创建日志。内容为空则拒绝。
     *
     * @param userId 提单用户
     * @param category 分类；空则记为 other
     * @param content 正文，不能为空白
     * @param orderId 关联订单，可空
     * @param priority 优先级；空则记为 normal
     * @param conversationId 关联对话，可空
     * @return 新建的工单
     */
    Ticket create(String userId, String category, String content, String orderId, String priority, String conversationId);

    /**
     * 改状态并记一条流水。状态值先收成枚举。
     *
     * @param ticketId 工单主键
     * @param status 新状态码
     * @param actorUserId 操作人用户主键
     * @return 更新后的工单
     */
    Ticket changeStatus(String ticketId, String status, String actorUserId);

    /**
     * 改优先级并记流水。工单状态不动。
     *
     * @param ticketId 工单主键
     * @param priority 新优先级
     * @param actorUserId 操作人用户主键
     * @return 更新后的工单
     */
    Ticket changePriority(String ticketId, String priority, String actorUserId);

    /**
     * 追加一条证据。工单状态不动，日志前后值记当前状态。
     *
     * @param ticketId 工单主键
     * @param content 证据正文
     * @param actorUserId 操作人用户主键
     * @return 更新时间已刷新的工单
     */
    Ticket evidence(String ticketId, String content, String actorUserId);

    /**
     * 追加一条回复。工单状态不动，操作人记为管理端。
     *
     * @param ticketId 工单主键
     * @param content 回复正文
     * @param ssoSub 回复人的统一身份
     * @return 更新时间已刷新的工单
     */
    Ticket reply(String ticketId, String content, String ssoSub);

    /**
     * 指定处理人并记流水。快照里留变更前后的值。
     *
     * @param ticketId 工单主键
     * @param ssoSub 新处理人的统一身份
     * @param actorSso 操作人的统一身份
     * @return 更新后的工单
     */
    Ticket assign(String ticketId, String ssoSub, String actorSso);

    /**
     * 列出该工单已经写下的日志。
     *
     * @param ticketId 工单主键
     * @return 日志列表
     */
    List<TicketLog> logs(String ticketId);
}
