package com.omino.shopai.admin.ticket;

import com.omino.shopai.admin.service.ticket.TicketCommands;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.Ticket;
import com.omino.shopai.entity.TicketLog;
import com.omino.shopai.enums.TicketLogKind;
import com.omino.shopai.enums.TicketStatus;
import com.omino.shopai.exception.ApiBusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TicketCommandTest {

    @Test
    void replyDoesNotCloseAndAClosedTicketRejectsAnotherReply() {
        Ticket ticket = new Ticket();
        ticket.setId("t1");
        ticket.setStatus(TicketStatus.OPEN);
        TicketLog reply = TicketCommands.reply(ticket, "先看一下", "ops");
        assertEquals(TicketLogKind.REPLY, reply.getKind());
        assertEquals(TicketStatus.OPEN, ticket.getStatus());

        List<TicketLog> closed = TicketCommands.change(ticket, "closed", null, null, "ops");
        assertEquals(1, closed.size());
        assertEquals(TicketLogKind.STATUS_CHANGE, closed.get(0).getKind());
        assertEquals("closed", closed.get(0).getToValue());
        assertEquals(TicketStatus.CLOSED, ticket.getStatus());

        ApiBusinessException rejected = assertThrows(ApiBusinessException.class,
                () -> TicketCommands.reply(ticket, "再回一句", "ops"));
        assertEquals(ErrorCodes.VALIDATION, rejected.code());
        assertEquals(TicketStatus.CLOSED, ticket.getStatus());
    }
}
