package com.omino.shopai.admin.service.ticket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.admin.support.AdminPages;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.dto.PageDto;
import com.omino.shopai.entity.Ticket;
import com.omino.shopai.entity.TicketLog;
import com.omino.shopai.enums.TicketStatus;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.TicketLogMapper;
import com.omino.shopai.mapper.TicketMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工单查询、回复和改派。回复与关单分开。
 */
@Service
public class AdminTicketService {

    private final TicketMapper tickets;
    private final TicketLogMapper logs;

    /**
     * 注入工单和它的处理记录。
     *
     * @param tickets 工单表
     * @param logs 处理记录
     */
    public AdminTicketService(TicketMapper tickets, TicketLogMapper logs) {
        this.tickets = tickets;
        this.logs = logs;
    }

    /**
     * 分页查询工单。
     *
     * @param userId 用户主键，可空
     * @param status 状态码，可空
     * @param page 页码
     * @param size 页大小
     * @return 工单页
     */
    public PageDto<Ticket> list(String userId, String status, Integer page, Integer size) {
        TicketStatus parsed = status == null || status.isBlank() ? null : TicketStatus.fromCode(status);
        List<Ticket> rows = tickets.selectList(new LambdaQueryWrapper<Ticket>()
                .eq(userId != null && !userId.isBlank(), Ticket::getUserId, userId)
                .eq(parsed != null, Ticket::getStatus, parsed)
                .orderByDesc(Ticket::getUpdatedAt));
        return AdminPages.of(rows, page, size);
    }

    /**
     * 工单当前值和时间线。
     *
     * @param id 工单主键
     * @return ticket 与 logs
     */
    public Map<String, Object> detail(String id) {
        Ticket ticket = require(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ticket", ticket);
        body.put("logs", logs.listByTicket(id));
        return body;
    }

    /**
     * 只追加回复。状态保持原值。已关闭则拒绝。
     *
     * @param id 工单主键
     * @param content 回复正文
     * @param ssoSub 操作人
     * @return 回复之后的工单，状态与回复前相同
     */
    @Transactional
    public Ticket reply(String id, String content, String ssoSub) {
        Ticket ticket = require(id);
        TicketLog log = TicketCommands.reply(ticket, content, ssoSub);
        // 先写回复记录，不改 status。关单必须走单独的状态变更。
        logs.insert(log);
        return ticket;
    }

    /**
     * 改状态、优先级或处理人。每一项变更和一条处理记录在同一事务。
     *
     * @param id 工单主键
     * @param status 新状态码，可空
     * @param priority 新优先级，可空
     * @param assignee 新处理人，可空
     * @param ssoSub 操作人
     * @return 更新后的工单
     */
    @Transactional
    public Ticket change(String id, String status, String priority, String assignee, String ssoSub) {
        Ticket ticket = require(id);
        List<TicketLog> written = TicketCommands.change(ticket, status, priority, assignee, ssoSub);
        for (TicketLog log : written) {
            logs.insert(log);
        }
        // 工单新值和处理记录一起提交。中途失败则两边都不留。
        tickets.updateById(ticket);
        return ticket;
    }

    private Ticket require(String id) {
        Ticket ticket = tickets.selectById(id);
        if (ticket == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "工单不存在");
        }
        return ticket;
    }
}
