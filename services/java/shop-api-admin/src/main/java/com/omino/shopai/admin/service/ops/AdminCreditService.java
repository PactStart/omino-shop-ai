package com.omino.shopai.admin.service.ops;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.admin.support.AdminPages;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.dto.PageDto;
import com.omino.shopai.entity.AdminAuditLog;
import com.omino.shopai.entity.CreditAccount;
import com.omino.shopai.entity.CreditLedger;
import com.omino.shopai.entity.CreditOrder;
import com.omino.shopai.enums.CreditLedgerType;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.AdminAuditLogMapper;
import com.omino.shopai.mapper.CreditAccountMapper;
import com.omino.shopai.mapper.CreditLedgerMapper;
import com.omino.shopai.mapper.CreditOrderMapper;
import com.omino.shopai.support.Ids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 积分比例只读，流水和调账。调账必须有备注并写审计。
 */
@Service
public class AdminCreditService {

    private final String tokensPerCredit;
    private final CreditOrderMapper orders;
    private final CreditLedgerMapper ledger;
    private final CreditAccountMapper accounts;
    private final AdminAuditLogMapper audits;

    /**
     * 注入比例和积分表。比例来自配置，缺省 1000。
     *
     * @param tokensPerCredit 多少 token 折 1 积分
     * @param orders 积分支付单
     * @param ledger 积分流水
     * @param accounts 积分账户
     * @param audits 审计
     */
    public AdminCreditService(
            @Value("${credit.tokens-per-credit:1000}") String tokensPerCredit,
            CreditOrderMapper orders,
            CreditLedgerMapper ledger,
            CreditAccountMapper accounts,
            AdminAuditLogMapper audits) {
        this.tokensPerCredit = tokensPerCredit;
        this.orders = orders;
        this.ledger = ledger;
        this.accounts = accounts;
        this.audits = audits;
    }

    /**
     * 当前 token 与积分的比例。只读。
     *
     * @return tokensPerCredit
     */
    public Map<String, String> rate() {
        return Map.of("tokensPerCredit", tokensPerCredit);
    }

    /**
     * 积分支付单分页。
     *
     * @param userId 用户主键，可空
     * @param page 页码
     * @param size 页大小
     * @return 支付单页
     */
    public PageDto<CreditOrder> orders(String userId, Integer page, Integer size) {
        List<CreditOrder> rows = orders.selectList(new LambdaQueryWrapper<CreditOrder>()
                .eq(userId != null && !userId.isBlank(), CreditOrder::getUserId, userId)
                .orderByDesc(CreditOrder::getCreatedAt));
        return AdminPages.of(rows, page, size);
    }

    /**
     * 积分流水分页。金额是两位小数字符串。
     *
     * @param userId 用户主键，可空
     * @param page 页码
     * @param size 页大小
     * @return 流水页
     */
    public PageDto<Map<String, Object>> ledger(String userId, Integer page, Integer size) {
        List<Map<String, Object>> rows = ledger.selectList(new LambdaQueryWrapper<CreditLedger>()
                        .eq(userId != null && !userId.isBlank(), CreditLedger::getUserId, userId)
                        .orderByDesc(CreditLedger::getCreatedAt))
                .stream()
                .map(this::ledgerRow)
                .toList();
        return AdminPages.of(rows, page, size);
    }

    /**
     * 调账。备注为空则拒绝。金额入库取绝对值，余额按正负变动，且不能变成负数。
     *
     * @param userId 用户主键
     * @param amount 带符号的金额字符串
     * @param memo 备注
     * @param ssoSub 操作人
     * @return 新的流水
     */
    @Transactional
    public CreditLedger adjust(String userId, String amount, String memo, String ssoSub) {
        if (memo == null || memo.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "调账备注不能为空");
        }
        if (userId == null || userId.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "userId 不能为空");
        }
        BigDecimal delta;
        try {
            delta = new BigDecimal(amount.trim());
        } catch (RuntimeException ex) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "金额不正确");
        }
        if (delta.signum() == 0) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "金额不能为 0");
        }
        CreditAccount account = accounts.selectById(userId);
        if (account == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "积分账户不存在");
        }
        BigDecimal next = account.getAvailable().add(delta);
        if (next.signum() < 0) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "积分余额不足");
        }
        account.setAvailable(next);
        account.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        accounts.updateById(account);
        CreditLedger row = new CreditLedger();
        row.setId(Ids.ulid());
        row.setUserId(userId);
        row.setType(CreditLedgerType.ADJUST);
        row.setAmount(delta.abs());
        row.setBalanceAfter(next);
        row.setMemo(memo.trim());
        row.setCreatedAt(account.getUpdatedAt());
        ledger.insert(row);
        AdminAuditLog audit = new AdminAuditLog();
        audit.setId(Ids.ulid());
        audit.setSsoSub(ssoSub == null ? "admin" : ssoSub);
        audit.setAction("credit.adjust");
        audit.setResource(userId);
        audit.setDetail("\"" + userId + "\"");
        audit.setCreatedAt(row.getCreatedAt());
        audits.insertJson(audit);
        return row;
    }

    private Map<String, Object> ledgerRow(CreditLedger item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("userId", item.getUserId());
        row.put("type", item.getType() == null ? null : item.getType().code());
        row.put("amount", AdminPages.money(item.getAmount()));
        row.put("balanceAfter", AdminPages.money(item.getBalanceAfter()));
        row.put("memo", item.getMemo());
        row.put("createdAt", item.getCreatedAt());
        return row;
    }
}
