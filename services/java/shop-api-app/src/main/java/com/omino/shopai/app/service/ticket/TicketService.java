package com.omino.shopai.app.service.ticket;

import com.omino.shopai.enums.TicketStatus;
import com.omino.shopai.enums.TicketLogKind;
import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.Ticket;
import com.omino.shopai.entity.TicketLog;
import com.omino.shopai.support.Ids;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
/**
 * TicketService 编排用例，事务和校验放在这里。
 */

@Service
public class TicketService {

    private final TicketBook book;
    private final Clock clock;
/**
 * 建单和变更都追加日志，不改已经写下的历史。
 */

    public TicketService(TicketBook book, Clock clock) {
        this.book = book;
        this.clock = clock;
    }
/**
 * 创建新记录。
 */

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
        book.insert(ticket, log(ticket.getId(), "created", "user", userId, null, null, "open", content.trim(), now));
        return ticket;
    }
/**
 * 改状态并记一条流水。状态值先收成枚举。
 */

    public Ticket changeStatus(String ticketId, String status, String actorUserId) {
        return change(ticketId, "status_change", status, null, actorUserId, null, ticket -> {
            String from = ticket.getStatus().code();
            ticket.setStatus(TicketStatus.fromCode(status));
            return from;
        });
    }
/**
 * 改优先级并记流水。工单状态不动。
 */

    public Ticket changePriority(String ticketId, String priority, String actorUserId) {
        return change(ticketId, "priority_change", priority, null, actorUserId, null, ticket -> {
            String from = ticket.getPriority();
            ticket.setPriority(priority);
            return from;
        });
    }

    public Ticket evidence(String ticketId, String content, String actorUserId) {
        return change(ticketId, "evidence", null, content, actorUserId, null, ticket -> ticket.getStatus().code());
    }

    public Ticket reply(String ticketId, String content, String ssoSub) {
        return change(ticketId, "reply", null, content, null, ssoSub, ticket -> ticket.getStatus().code());
    }
/**
 * 指定处理人并记流水。快照里留变更前后的值。
 */

    public Ticket assign(String ticketId, String ssoSub, String actorSso) {
        return change(ticketId, "assignee_change", ssoSub, null, null, actorSso, ticket -> {
            String from = ticket.getAssigneeSsoSub();
            ticket.setAssigneeSsoSub(ssoSub);
            return from;
        });
    }

    public List<TicketLog> logs(String ticketId) {
        return book.logs(ticketId);
    }
/**
 * 先改工单再追加一条日志。找不到工单则 40400。
 */

    private Ticket change(String ticketId, String kind, String to, String content, String actorUserId, String actorSso, java.util.function.Function<Ticket, String> apply) {
        Ticket ticket = book.find(ticketId);
        if (ticket == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "工单不存在");
        }
        String from = apply.apply(ticket);
        ticket.setUpdatedAt(OffsetDateTime.now(clock));
        // 只追加一条日志，不回写已经存在的历史行。
        book.save(ticket, log(ticketId, kind, actorSso == null ? "user" : "admin", actorUserId, actorSso, from, to, content, ticket.getUpdatedAt()));
        return ticket;
    }
/**
 * 组装一条日志。种类收成枚举，前后值仍存字符串快照。
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
