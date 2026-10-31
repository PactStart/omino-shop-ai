package com.omino.shopai.admin.service.ops;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.entity.OrderSyncCursor;
import com.omino.shopai.entity.Ticket;
import com.omino.shopai.entity.Withdrawal;
import com.omino.shopai.enums.WithdrawalStatus;
import com.omino.shopai.mapper.OrderSyncCursorMapper;
import com.omino.shopai.mapper.TicketMapper;
import com.omino.shopai.mapper.WithdrawalMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 工作台三个数字。没有行时是 0，不编造。
 */
@Service
public class AdminDashboardService {

    private final OrderSyncCursorMapper cursors;
    private final TicketMapper tickets;
    private final WithdrawalMapper withdrawals;

    /**
     * 注入三个计数来源。
     *
     * @param cursors 同步游标
     * @param tickets 工单
     * @param withdrawals 提现
     */
    public AdminDashboardService(OrderSyncCursorMapper cursors, TicketMapper tickets, WithdrawalMapper withdrawals) {
        this.cursors = cursors;
        this.tickets = tickets;
        this.withdrawals = withdrawals;
    }

    /**
     * 同步失败平台数、工单行数、待审提现数。
     *
     * @return syncFailures、ticketCount、pendingWithdrawals
     */
    public Map<String, Long> counts() {
        long syncFailures = cursors.selectList(null).stream()
                .filter(this::failed)
                .count();
        Long ticketCount = tickets.selectCount(null);
        Long pending = withdrawals.selectCount(new LambdaQueryWrapper<Withdrawal>()
                .eq(Withdrawal::getStatus, WithdrawalStatus.PENDING));
        return Map.of(
                "syncFailures", syncFailures,
                "ticketCount", ticketCount == null ? 0L : ticketCount,
                "pendingWithdrawals", pending == null ? 0L : pending);
    }

    private boolean failed(OrderSyncCursor cursor) {
        return cursor.getLastError() != null && !cursor.getLastError().isBlank();
    }
}
