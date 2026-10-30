package com.omino.shopai.app.support;

import com.omino.shopai.app.service.kb.KbSearchService;
import com.omino.shopai.app.service.ticket.ITicket;
import com.omino.shopai.app.service.ticket.TicketService;
import com.omino.shopai.entity.Ticket;
import com.omino.shopai.entity.TicketLog;
import com.omino.shopai.enums.TicketLogKind;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TicketLogTest {

    @Test
    void missIsExplicitAndStatusChangeAppendsWithoutRewritingHistory() {
        KbSearchService search = new KbSearchService(() -> List.of());
        KbSearchService.KbHitDto miss = search.search("不存在的退款政策");
        assertFalse(miss.hit());
        assertEquals(0, miss.articles().size());
        MemoryTickets book = new MemoryTickets();
        TicketService tickets = new TicketService(book, Clock.systemUTC());
        Ticket created = tickets.create("01USER0000000000000000000A", "order", "没找到规则", null, "normal", null);
        assertEquals(TicketLogKind.CREATED, book.logs.get(0).getKind());
        tickets.changeStatus(created.getId(), "processing", "sso-1");
        assertEquals(2, book.logs.size());
        assertEquals(TicketLogKind.CREATED, book.logs.get(0).getKind());
        assertEquals("open", book.logs.get(0).getToValue());
        assertEquals(TicketLogKind.STATUS_CHANGE, book.logs.get(1).getKind());
        assertEquals("processing", book.logs.get(1).getToValue());
    }

    private static final class MemoryTickets implements ITicket {
        private final List<Ticket> tickets = new ArrayList<>();
        private final List<TicketLog> logs = new ArrayList<>();

        @Override
        public void insert(Ticket ticket, TicketLog created) {
            tickets.add(ticket);
            logs.add(created);
        }

        @Override
        public Ticket find(String id) {
            return tickets.stream().filter(row -> id.equals(row.getId())).findFirst().orElse(null);
        }

        @Override
        public void save(Ticket ticket, TicketLog extra) {
            for (int i = 0; i < tickets.size(); i++) {
                if (ticket.getId().equals(tickets.get(i).getId())) {
                    tickets.set(i, ticket);
                    logs.add(extra);
                    return;
                }
            }
        }

        @Override
        public List<TicketLog> logs(String ticketId) {
            return logs.stream().filter(row -> ticketId.equals(row.getTicketId())).toList();
        }
    }
}
