package com.omino.shopai.admin.service.wallet;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.admin.support.AdminPages;
import com.omino.shopai.dto.PageDto;
import com.omino.shopai.entity.WalletLedger;
import com.omino.shopai.mapper.WalletLedgerMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 返佣流水只读。历史行不能改。
 */
@Service
public class AdminWalletLedgerService {

    private final WalletLedgerMapper ledger;

    /**
     * 注入流水表。
     *
     * @param ledger 钱包流水
     */
    public AdminWalletLedgerService(WalletLedgerMapper ledger) {
        this.ledger = ledger;
    }

    /**
     * 按时间倒序分页。用户详情必须带 userId。
     *
     * @param userId 用户主键，可空
     * @param page 页码
     * @param size 页大小
     * @return 流水页，金额是两位小数字符串
     */
    public PageDto<Map<String, Object>> list(String userId, Integer page, Integer size) {
        List<Map<String, Object>> rows = ledger.selectList(new LambdaQueryWrapper<WalletLedger>()
                        .eq(userId != null && !userId.isBlank(), WalletLedger::getUserId, userId)
                        .orderByDesc(WalletLedger::getCreatedAt))
                .stream()
                .map(this::row)
                .toList();
        return AdminPages.of(rows, page, size);
    }

    private Map<String, Object> row(WalletLedger item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("userId", item.getUserId());
        row.put("type", item.getType() == null ? null : item.getType().code());
        row.put("amount", AdminPages.money(item.getAmount()));
        row.put("orderId", item.getOrderId());
        row.put("withdrawalId", item.getWithdrawalId());
        row.put("memo", item.getMemo());
        row.put("createdAt", item.getCreatedAt());
        return row;
    }
}
