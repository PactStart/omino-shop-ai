package com.omino.shopai.app.service.credit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.app.service.chat.ChatRun;
import com.omino.shopai.app.service.chat.IChatEventService;
import com.omino.shopai.app.service.chat.ICreditGate;
import com.omino.shopai.entity.CreditAccount;
import com.omino.shopai.entity.CreditPackage;
import com.omino.shopai.enums.ActiveStatus;
import com.omino.shopai.mapper.CreditAccountMapper;
import com.omino.shopai.mapper.CreditPackageMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开 run 前看积分够不够。不够则写下购积分卡和终态，不再调用工人。
 */
@Service
public class CreditBillingImpl implements ICreditGate {

    private final CreditAccountMapper accounts;
    private final CreditPackageMapper packages;
    private final IChatEventService events;
    private final boolean enforce;
    private final BigDecimal tokensPerCredit;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * 注入积分账户、套餐和事件流。未强制扣费时一律放行。
     *
     * @param accounts 积分账户
     * @param packages 积分套餐
     * @param events 事件流
     * @param enforce 是否在余额不足时拦住开 run
     * @param tokensPerCredit 多少 token 折合一积分
     */
    public CreditBillingImpl(
            CreditAccountMapper accounts,
            CreditPackageMapper packages,
            IChatEventService events,
            @Value("${credit.enforce:false}") boolean enforce,
            @Value("${credit.tokens-per-credit:1000}") String tokensPerCredit) {
        this.accounts = accounts;
        this.packages = packages;
        this.events = events;
        this.enforce = enforce;
        this.tokensPerCredit = new BigDecimal(tokensPerCredit);
    }

    /**
     * 余额不足时写下购积分卡并拦住这一轮。
     *
     * @param run 刚开的一轮
     * @return 需要拦住时为真
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

    /**
     * 读取当前可用积分和折算比例。
     *
     * @param userId 用户主键
     * @return available 与 tokensPerCredit
     */
    public Map<String, String> balance(String userId) {
        return Map.of(
                "available", CreditPurchaseServiceImpl.money(available(userId)).toPlainString(),
                "tokensPerCredit", tokensPerCredit.toPlainString());
    }

    /**
     * 列出可购买的积分套餐。
     *
     * @return 生效套餐，按排序号升序
     */
    public List<CreditPackage> packages() {
        return loadPackages();
    }

    /**
     * 读当前可用积分，供开 run 前判断够不够。
     *
     * @param userId 用户主键
     * @return 可用积分；没有账户时为零
     */
    private BigDecimal available(String userId) {
        CreditAccount account = loadAccount(userId);
        if (account == null || account.getAvailable() == null) {
            return BigDecimal.ZERO;
        }
        return account.getAvailable();
    }

    /**
     * 余额不足时给出购积分卡文案，由 SSE 再推终态。
     *
     * @param userId 用户主键
     * @return 购积分卡 JSON
     */
    private String card(String userId) {
        List<Map<String, Object>> rows = loadPackages().stream().map(pack -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", pack.getId());
            row.put("name", pack.getName());
            row.put("credits", CreditPurchaseServiceImpl.money(pack.getCredits()).toPlainString());
            row.put("price", CreditPurchaseServiceImpl.money(pack.getPrice()).toPlainString());
            row.put("sort", pack.getSort());
            return row;
        }).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", "INSUFFICIENT_START");
        body.put("balance", CreditPurchaseServiceImpl.money(available(userId)).toPlainString());
        body.put("packages", rows);
        try {
            return json.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            return "{\"reason\":\"INSUFFICIENT_START\"}";
        }
    }

    /**
     * 读取积分账户。
     *
     * @param userId 用户主键
     * @return 积分账户；没有则空
     */
    protected CreditAccount loadAccount(String userId) {
        return accounts.selectById(userId);
    }

    /**
     * 列出生效套餐，排序号小的在前。
     *
     * @return 生效套餐
     */
    protected List<CreditPackage> loadPackages() {
        return packages.selectList(new LambdaQueryWrapper<CreditPackage>()
                .eq(CreditPackage::getStatus, ActiveStatus.ACTIVE)
                .orderByAsc(CreditPackage::getSort));
    }
}
