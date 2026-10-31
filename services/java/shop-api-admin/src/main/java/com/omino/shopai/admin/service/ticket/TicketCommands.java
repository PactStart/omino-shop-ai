package com.omino.shopai.admin.service.ticket;

import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.Ticket;
import com.omino.shopai.entity.TicketLog;
import com.omino.shopai.enums.TicketLogKind;
import com.omino.shopai.enums.TicketStatus;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.support.Ids;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 工单回复和改状态。回复不改状态。关单必须是一次明确的状态变更。
 */
public final class TicketCommands {

    private TicketCommands() {
    }

    /**
     * 追加一条回复。已关闭则拒绝，工单状态保持不动。
     *
     * @param ticket 当前工单
     * @param content 回复正文
     * @param ssoSub 操作人
     * @return 要插入的回复记录
     */
    public static TicketLog reply(Ticket ticket, String content, String ssoSub) {
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "已关闭的工单不能再回复");
        }
        if (content == null || content.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "回复不能为空");
        }
        TicketLog log = base(ticket, TicketLogKind.REPLY, ssoSub);
        log.setContent(content.trim());
        return log;
    }

    /**
     * 按传入的字段追加处理记录，并改工单上对应的值。没变的字段不写记录。
     *
     * @param ticket 当前工单，会被就地修改
     * @param status 新状态码，可空
     * @param priority 新优先级，可空
     * @param assignee 新处理人，可空
     * @param ssoSub 操作人
     * @return 本次要插入的处理记录
     */
    public static List<TicketLog> change(Ticket ticket, String status, String priority, String assignee, String ssoSub) {
        List<TicketLog> logs = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            TicketStatus next = TicketStatus.fromCode(status.trim());
            if (next == null) {
                throw new ApiBusinessException(ErrorCodes.VALIDATION, "状态不正确");
            }
            if (next != ticket.getStatus()) {
                TicketLog log = base(ticket, TicketLogKind.STATUS_CHANGE, ssoSub);
                log.setFromValue(ticket.getStatus() == null ? null : ticket.getStatus().code());
                log.setToValue(next.code());
                logs.add(log);
                ticket.setStatus(next);
            }
        }
        if (priority != null && !priority.equals(ticket.getPriority())) {
            TicketLog log = base(ticket, TicketLogKind.PRIORITY_CHANGE, ssoSub);
            log.setFromValue(ticket.getPriority());
            log.setToValue(priority);
            logs.add(log);
            ticket.setPriority(priority);
        }
        if (assignee != null && !assignee.equals(ticket.getAssigneeSsoSub())) {
            TicketLog log = base(ticket, TicketLogKind.ASSIGNEE_CHANGE, ssoSub);
            log.setFromValue(ticket.getAssigneeSsoSub());
            log.setToValue(assignee);
            logs.add(log);
            ticket.setAssigneeSsoSub(assignee);
        }
        ticket.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return logs;
    }

    private static TicketLog base(Ticket ticket, TicketLogKind kind, String ssoSub) {
        TicketLog log = new TicketLog();
        log.setId(Ids.ulid());
        log.setTicketId(ticket.getId());
        log.setKind(kind);
        log.setActorType("admin");
        log.setActorSsoSub(ssoSub == null ? "admin" : ssoSub);
        // payload 是必填 jsonb。没有额外证据时写空对象，避免回复插不进去。
        log.setPayload("{}");
        log.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return log;
    }
}
