package com.omino.shopai.app.service.ticket;

import com.omino.shopai.entity.Ticket;
import com.omino.shopai.entity.TicketLog;
import com.omino.shopai.mapper.TicketLogMapper;
import com.omino.shopai.mapper.TicketMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
/**
 * 持久化边界。事务和状态判断由调用方负责。
 */

@Repository
public class TicketImpl implements ITicket {

    private final TicketMapper tickets;
    private final TicketLogMapper logs;
    private final TransactionTemplate transactions;
/**
 * 工单和日志的 MyBatis 访问。改状态不在这里做。
 */

    public TicketImpl(TicketMapper tickets, TicketLogMapper logs, TransactionTemplate transactions) {
        this.tickets = tickets;
        this.logs = logs;
        this.transactions = transactions;
    }
/**
 * 插入一行。
 */

    @Override
    public void insert(Ticket ticket, TicketLog created) {
        transactions.executeWithoutResult(status -> {
            tickets.insert(ticket);
            logs.insert(created);
        });
    }

    @Override
    public Ticket find(String id) {
        return tickets.selectById(id);
    }
/**
 * 保存修改。
 */

    @Override
    public void save(Ticket ticket, TicketLog extra) {
        transactions.executeWithoutResult(status -> {
            tickets.updateById(ticket);
            logs.insert(extra);
        });
    }

    @Override
    public List<TicketLog> logs(String ticketId) {
        return logs.listByTicket(ticketId);
    }
}
