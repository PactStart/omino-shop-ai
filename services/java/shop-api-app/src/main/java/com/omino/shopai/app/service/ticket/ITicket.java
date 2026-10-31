package com.omino.shopai.app.service.ticket;

import com.omino.shopai.entity.Ticket;
import com.omino.shopai.entity.TicketLog;

import java.util.List;
/**
 * 工单和日志的存储端口。
 */

public interface ITicket {

    void insert(Ticket ticket, TicketLog created);

    Ticket find(String id);

    void save(Ticket ticket, TicketLog extra);

    List<TicketLog> logs(String ticketId);
}
