package com.omino.shopai.app.service.credit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.app.service.chat.ChatRun;
import com.omino.shopai.app.service.chat.ICreditGate;
import com.omino.shopai.app.service.chat.IRunEventLog;
import com.omino.shopai.entity.CreditAccount;
import com.omino.shopai.entity.CreditPackage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * CreditBillingImpl 编排用例，事务和校验放在这里。
 */

@Service
public class CreditBillingImpl implements ICreditGate {

    private final ICredit book;
    private final IRunEventLog events;
    private final boolean enforce;
    private final BigDecimal tokensPerCredit;
    private final ObjectMapper json = new ObjectMapper();

    public CreditBillingImpl(
            ICredit book,
            IRunEventLog events,
            @Value("${credit.enforce:false}") boolean enforce,
            @Value("${credit.tokens-per-credit:1000}") String tokensPerCredit) {
        this.book = book;
        this.events = events;
        this.enforce = enforce;
        this.tokensPerCredit = new BigDecimal(tokensPerCredit);
    }
/**
 * 判断是否拦截本次调用。
 */

    @Override
    public boolean block(ChatRun run) {
        if (!enforce || available(run.userId()).compareTo(new BigDecimal("0.01")) >= 0) {
            return false;
        }
        events.append(run.runId(), "credit_card", card(run.userId()));
        events.append(run.runId(), "credit_exhausted", "{\"runId\":\"" + run.runId() + "\",\"reason\":\"INSUFFICIENT_START\"}");
        events.expire(run.runId());
        return true;
    }

    public Map<String, String> balance(String userId) {
        return Map.of(
                "available", CreditPurchaseService.money(available(userId)).toPlainString(),
                "tokensPerCredit", tokensPerCredit.toPlainString());
    }

    public List<CreditPackage> packages() {
        return book.activePackages();
    }
/**
 * 读当前可用积分，供开 run 前判断够不够。
 */

    private BigDecimal available(String userId) {
        CreditAccount account = book.credit(userId);
        if (account == null || account.getAvailable() == null) {
            return BigDecimal.ZERO;
        }
        return account.getAvailable();
    }
/**
 * 余额不足时给出购积分卡文案，由 SSE 再推终态。
 */

    private String card(String userId) {
        List<Map<String, Object>> packages = book.activePackages().stream().map(pack -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", pack.getId());
            row.put("name", pack.getName());
            row.put("credits", CreditPurchaseService.money(pack.getCredits()).toPlainString());
            row.put("price", CreditPurchaseService.money(pack.getPrice()).toPlainString());
            row.put("sort", pack.getSort());
            return row;
        }).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", "INSUFFICIENT_START");
        body.put("balance", CreditPurchaseService.money(available(userId)).toPlainString());
        body.put("packages", packages);
        try {
            return json.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            return "{\"reason\":\"INSUFFICIENT_START\"}";
        }
    }
}
