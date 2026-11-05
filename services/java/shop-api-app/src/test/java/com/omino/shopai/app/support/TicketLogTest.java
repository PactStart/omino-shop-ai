package com.omino.shopai.app.support;

import com.omino.shopai.app.dto.KbHitDto;
import com.omino.shopai.app.service.kb.IKbSearchService;
import com.omino.shopai.app.service.kb.KbSearchServiceImpl;
import com.omino.shopai.entity.KbArticle;
import com.omino.shopai.mapper.KbArticleMapper;
import com.omino.shopai.app.service.ticket.ITicketService;
import com.omino.shopai.app.service.ticket.TicketServiceImpl;
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
        IKbSearchService search = new EmptyKb();
        KbHitDto miss = search.search("不存在的退款政策");
        assertFalse(miss.hit());
        assertEquals(0, miss.articles().size());
        MemoryTickets book = new MemoryTickets();
        ITicketService tickets = new MemoryTicketService(book);
        Ticket created = tickets.create("01USER0000000000000000000A", "order", "没找到规则", null, "normal", null);
        assertEquals(TicketLogKind.CREATED, book.logs.get(0).getKind());
        tickets.changeStatus(created.getId(), "processing", "sso-1");
        assertEquals(2, book.logs.size());
        assertEquals(TicketLogKind.CREATED, book.logs.get(0).getKind());
        assertEquals("open", book.logs.get(0).getToValue());
        assertEquals(TicketLogKind.STATUS_CHANGE, book.logs.get(1).getKind());
        assertEquals("processing", book.logs.get(1).getToValue());
    }

    /**
     * 没有文章的检索。未命中判断仍走真实实现。
     */
    private static final class EmptyKb extends KbSearchServiceImpl {

        /**
         * 不连接文章表。检索时由子类直接返回空列表。
         */
        private EmptyKb() {
            super((KbArticleMapper) null);
        }

        /**
         * 不查表，直接返回空文章列表。
         *
         * @return 空列表
         */
        @Override
        protected List<KbArticle> published() {
            return List.of();
        }
    }

    /**
     * 用内存账本替换工单用例里的表访问，业务方法仍走真实实现。
     */
    static final class MemoryTicketService extends TicketServiceImpl {

        private final MemoryTickets book;

        /**
         * 绑定同一份内存账本。表访问全部转给它。
         *
         * @param book 内存账本
         */
        MemoryTicketService(MemoryTickets book) {
            super(null, null, null, Clock.systemUTC());
            this.book = book;
        }

        /**
         * 把工单和创建日志写入内存账本。
         *
         * @param ticket 新工单
         * @param created 创建日志
         * @return 无
         */
        @Override
        protected void insertPair(Ticket ticket, TicketLog created) {
            book.insert(ticket, created);
        }

        /**
         * 从内存账本按主键读取工单。
         *
         * @param id 工单主键
         * @return 工单；没有则空
         */
        @Override
        protected Ticket findTicket(String id) {
            return book.find(id);
        }

        /**
         * 把工单改动和追加日志写入内存账本。
         *
         * @param ticket 已改过的工单
         * @param extra 本次追加的日志
         * @return 无
         */
        @Override
        protected void savePair(Ticket ticket, TicketLog extra) {
            book.save(ticket, extra);
        }

        /**
         * 从内存账本列出该工单的日志。
         *
         * @param ticketId 工单主键
         * @return 日志列表
         */
        @Override
        protected List<TicketLog> listLogs(String ticketId) {
            return book.logs(ticketId);
        }
    }

    private static final class MemoryTickets {
        private final List<Ticket> tickets = new ArrayList<>();
        private final List<TicketLog> logs = new ArrayList<>();

        /**
         * 记下新工单和它的创建日志。
         *
         * @param ticket 新工单
         * @param created 创建日志
         * @return 无
         */
        void insert(Ticket ticket, TicketLog created) {
            tickets.add(ticket);
            logs.add(created);
        }

        /**
         * 按主键找内存里的工单。
         *
         * @param id 工单主键
         * @return 工单；没有则空
         */
        Ticket find(String id) {
            return tickets.stream().filter(row -> id.equals(row.getId())).findFirst().orElse(null);
        }

        /**
         * 替换同主键的工单并追加一条日志。
         *
         * @param ticket 已改过的工单
         * @param extra 本次追加的日志
         * @return 无
         */
        void save(Ticket ticket, TicketLog extra) {
            for (int i = 0; i < tickets.size(); i++) {
                if (ticket.getId().equals(tickets.get(i).getId())) {
                    tickets.set(i, ticket);
                    logs.add(extra);
                    return;
                }
            }
        }

        /**
         * 列出该工单在内存里的日志。
         *
         * @param ticketId 工单主键
         * @return 日志列表
         */
        List<TicketLog> logs(String ticketId) {
            return logs.stream().filter(row -> ticketId.equals(row.getTicketId())).toList();
        }
    }
}
