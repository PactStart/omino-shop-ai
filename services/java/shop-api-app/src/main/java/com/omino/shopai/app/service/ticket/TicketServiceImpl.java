package com.omino.shopai.app.service.ticket;

import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.Ticket;
import com.omino.shopai.entity.TicketLog;
import com.omino.shopai.enums.TicketLogKind;
import com.omino.shopai.enums.TicketStatus;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.TicketLogMapper;
import com.omino.shopai.mapper.TicketMapper;
import com.omino.shopai.support.Ids;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;

/**
 * 工单用例。建单、改状态和追加日志都走这里，历史日志只追加不回写。
 */
@Service
public class TicketServiceImpl implements ITicketService {

    private final TicketMapper tickets;
    private final TicketLogMapper logs;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * 注入工单表、日志表、事务和时钟。建单与改单的事务仍包住成对写入。
     *
     * @param tickets 工单表
     * @param logs 工单日志表
     * @param transactions 写入工单和日志的事务
     * @param clock 创建和更新时间
     */
    public TicketServiceImpl(TicketMapper tickets, TicketLogMapper logs, TransactionTemplate transactions, Clock clock) {
        this.tickets = tickets;
        this.logs = logs;
        this.transactions = transactions;
        this.clock = clock;
    }

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
    @Override
    public Ticket create(String userId, String category, String content, String orderId, String priority, String conversationId) {
        if (content == null || content.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "内容不能为空");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        Ticket ticket = new Ticket();
        ticket.setId(Ids.ulid());
        ticket.setUserId(userId);
        ticket.setConversationId(conversationId);
        ticket.setCategory(category == null || category.isBlank() ? "other" : category);
        ticket.setContent(content.trim());
        ticket.setOrderId(orderId);
        ticket.setPriority(priority == null || priority.isBlank() ? "normal" : priority);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        insertPair(ticket, log(ticket.getId(), "created", "user", userId, null, null, "open", content.trim(), now));
        return ticket;
    }

    /**
     * 改状态并记一条流水。状态值先收成枚举。
     *
     * @param ticketId 工单主键
     * @param status 新状态码
     * @param actorUserId 操作人用户主键
     * @return 更新后的工单
     */
    @Override
    public Ticket changeStatus(String ticketId, String status, String actorUserId) {
        return change(ticketId, "status_change", status, null, actorUserId, null, ticket -> {
            String from = ticket.getStatus().code();
            ticket.setStatus(TicketStatus.fromCode(status));
            return from;
        });
    }

    /**
     * 改优先级并记流水。工单状态不动。
     *
     * @param ticketId 工单主键
     * @param priority 新优先级
     * @param actorUserId 操作人用户主键
     * @return 更新后的工单
     */
    @Override
    public Ticket changePriority(String ticketId, String priority, String actorUserId) {
        return change(ticketId, "priority_change", priority, null, actorUserId, null, ticket -> {
            String from = ticket.getPriority();
            ticket.setPriority(priority);
            return from;
        });
    }

    /**
     * 追加一条证据。工单状态不动，日志前后值记当前状态。
     *
     * @param ticketId 工单主键
     * @param content 证据正文
     * @param actorUserId 操作人用户主键
     * @return 更新时间已刷新的工单
     */
    @Override
    public Ticket evidence(String ticketId, String content, String actorUserId) {
        return change(ticketId, "evidence", null, content, actorUserId, null, ticket -> ticket.getStatus().code());
    }

    /**
     * 追加一条回复。工单状态不动，操作人记为管理端。
     *
     * @param ticketId 工单主键
     * @param content 回复正文
     * @param ssoSub 回复人的统一身份
     * @return 更新时间已刷新的工单
     */
    @Override
    public Ticket reply(String ticketId, String content, String ssoSub) {
        return change(ticketId, "reply", null, content, null, ssoSub, ticket -> ticket.getStatus().code());
    }

    /**
     * 指定处理人并记流水。快照里留变更前后的值。
     *
     * @param ticketId 工单主键
     * @param ssoSub 新处理人的统一身份
     * @param actorSso 操作人的统一身份
     * @return 更新后的工单
     */
    @Override
    public Ticket assign(String ticketId, String ssoSub, String actorSso) {
        return change(ticketId, "assignee_change", ssoSub, null, null, actorSso, ticket -> {
            String from = ticket.getAssigneeSsoSub();
            ticket.setAssigneeSsoSub(ssoSub);
            return from;
        });
    }

    /**
     * 列出该工单已经写下的日志。
     *
     * @param ticketId 工单主键
     * @return 日志列表
     */
    @Override
    public List<TicketLog> logs(String ticketId) {
        return listLogs(ticketId);
    }

    /**
     * 同一事务插入工单和创建日志。两行都成功才提交。
     *
     * @param ticket 新工单
     * @param created 对应的创建日志
     * @return 无
     */
    protected void insertPair(Ticket ticket, TicketLog created) {
        transactions.executeWithoutResult(status -> {
            tickets.insert(ticket);
            logs.insert(created);
        });
    }

    /**
     * 按主键读取工单。不包事务，改单时先读再写。
     *
     * @param id 工单主键
     * @return 工单；没有则空
     */
    protected Ticket findTicket(String id) {
        return tickets.selectById(id);
    }

    /**
     * 同一事务更新工单并追加一条日志。不改已经写下的历史行。
     *
     * @param ticket 已改过的工单
     * @param extra 本次追加的日志
     * @return 无
     */
    protected void savePair(Ticket ticket, TicketLog extra) {
        transactions.executeWithoutResult(status -> {
            tickets.updateById(ticket);
            logs.insert(extra);
        });
    }

    /**
     * 列出该工单的日志。
     *
     * @param ticketId 工单主键
     * @return 日志列表
     */
    protected List<TicketLog> listLogs(String ticketId) {
        return logs.listByTicket(ticketId);
    }

    /**
     * 先改工单再追加一条日志。找不到工单则 40400。
     *
     * @param ticketId 工单主键
     * @param kind 日志种类码
     * @param to 变更后的快照，可空
     * @param content 日志正文，可空
     * @param actorUserId 用户侧操作人，可空
     * @param actorSso 管理侧操作人；有值时操作人类型为 admin
     * @param apply 改工单并返回变更前的快照
     * @return 更新后的工单
     */
    private Ticket change(String ticketId, String kind, String to, String content, String actorUserId, String actorSso, Function<Ticket, String> apply) {
        Ticket ticket = findTicket(ticketId);
        if (ticket == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "工单不存在");
        }
        String from = apply.apply(ticket);
        ticket.setUpdatedAt(OffsetDateTime.now(clock));
        // 只追加一条日志，不回写已经存在的历史行。
        savePair(ticket, log(ticketId, kind, actorSso == null ? "user" : "admin", actorUserId, actorSso, from, to, content, ticket.getUpdatedAt()));
        return ticket;
    }

    /**
     * 组装一条日志。种类收成枚举，前后值仍存字符串快照。
     *
     * @param ticketId 工单主键
     * @param kind 日志种类码
     * @param actorType 操作人类型
     * @param actorUserId 用户侧操作人，可空
     * @param actorSso 管理侧操作人，可空
     * @param from 变更前快照，可空
     * @param to 变更后快照，可空
     * @param content 正文，可空
     * @param now 写入时间
     * @return 尚未入库的日志
     */
    private static TicketLog log(
            String ticketId, String kind, String actorType, String actorUserId, String actorSso,
            String from, String to, String content, OffsetDateTime now) {
        TicketLog row = new TicketLog();
        row.setId(Ids.ulid());
        row.setTicketId(ticketId);
        row.setKind(TicketLogKind.fromCode(kind));
        row.setActorType(actorType);
        row.setActorUserId(actorUserId);
        row.setActorSsoSub(actorSso);
        row.setFromValue(from);
        row.setToValue(to);
        row.setContent(content);
        row.setPayload("{}");
        row.setCreatedAt(now);
        return row;
    }
}
