package com.omino.shopai.app.service.credit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.omino.shopai.app.service.chat.AgentAborted;
import com.omino.shopai.app.service.chat.ChatRun;
import com.omino.shopai.app.service.chat.IChatEventService;
import com.omino.shopai.app.service.chat.ICreditGate;
import com.omino.shopai.app.service.chat.IUsageBilling;
import com.omino.shopai.entity.CreditAccount;
import com.omino.shopai.entity.CreditLedger;
import com.omino.shopai.entity.CreditPackage;
import com.omino.shopai.enums.ActiveStatus;
import com.omino.shopai.enums.CreditLedgerType;
import com.omino.shopai.mapper.CreditAccountMapper;
import com.omino.shopai.mapper.CreditLedgerMapper;
import com.omino.shopai.mapper.CreditPackageMapper;
import com.omino.shopai.support.Ids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 开 run 前看积分够不够。不够则写下购积分卡和终态，不再调用工人。
 */
@Service
public class CreditBillingImpl implements ICreditGate, IUsageBilling {

    private static final BigDecimal FLOOR = new BigDecimal("0.01");

    private final CreditAccountMapper accounts;
    private final CreditPackageMapper packages;
    private final CreditLedgerMapper ledgers;
    private final IChatEventService events;
    private final boolean enforce;
    private final BigDecimal tokensPerCredit;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Integer> billedByRun = new ConcurrentHashMap<>();

    /**
     * 注入积分账户、套餐和事件流。未强制扣费时一律放行。
     *
     * @param accounts 积分账户
     * @param packages 积分套餐
     * @param events 事件流
     * @param enforce 是否在余额不足时拦住开 run
     * @param tokensPerCredit 多少 token 折合一积分
     * @param ledgers 积分流水，用来算本轮已经扣过的金额
     */
    public CreditBillingImpl(
            CreditAccountMapper accounts,
            CreditPackageMapper packages,
            IChatEventService events,
            boolean enforce,
            String tokensPerCredit) {
        this(accounts, packages, events, enforce, tokensPerCredit, null);
    }

    /**
     * 注入积分账户、套餐、事件流和流水。
     *
     * @param accounts 积分账户
     * @param packages 积分套餐
     * @param events 事件流
     * @param enforce 是否在余额不足时拦住开 run
     * @param tokensPerCredit 多少 token 折合一积分
     * @param ledgers 积分流水，用来算本轮已经扣过的金额
     */
    @org.springframework.beans.factory.annotation.Autowired
    public CreditBillingImpl(
            CreditAccountMapper accounts,
            CreditPackageMapper packages,
            IChatEventService events,
            @Value("${credit.enforce:false}") boolean enforce,
            @Value("${credit.tokens-per-credit:1000}") String tokensPerCredit,
            CreditLedgerMapper ledgers) {
        this.accounts = accounts;
        this.packages = packages;
        this.events = events;
        this.enforce = enforce;
        this.tokensPerCredit = new BigDecimal(tokensPerCredit);
        this.ledgers = ledgers;
    }

    /**
     * 用量按本轮累计算应扣，再减去已经扣过的金额。结束时多退少补。
     *
     * @param run 这一轮
     * @param name 事件名
     * @param data 事件正文
     */
    @Override
    @Transactional
    public void onEvent(ChatRun run, String name, String data) {
        if (!enforce || ledgers == null) {
            return;
        }
        if ("usage".equals(name)) {
            int billed = readBilled(data);
            billedByRun.put(run.runId(), billed);
            apply(run, billed, false);
        } else if ("done".equals(name)) {
            apply(run, billedByRun.getOrDefault(run.runId(), 0), true);
        }
    }

    /**
     * 累计 token 折成应扣金额。大于 0 且不足 0.01 时，本轮只抬到 0.01 一次。
     *
     * @param billedTokens 本轮累计 token
     * @param tokensPerCredit 多少 token 折合一积分
     * @return 本轮应扣金额
     */
    public static BigDecimal owed(long billedTokens, BigDecimal tokensPerCredit) {
        if (billedTokens <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal credits = BigDecimal.valueOf(billedTokens).divide(tokensPerCredit, 2, RoundingMode.HALF_UP);
        if (credits.compareTo(FLOOR) < 0) {
            return FLOOR;
        }
        return credits;
    }

    /**
     * 本次实扣等于应扣减去已经扣过的金额。同一累计值再到达则为 0。
     *
     * @param owed 本轮应扣
     * @param already 本轮已经扣过的金额
     * @return 本次增量，不为负
     */
    public static BigDecimal increment(BigDecimal owed, BigDecimal already) {
        BigDecimal delta = owed.subtract(already);
        if (delta.signum() < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return delta;
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

    /**
     * 按累计应扣和已扣差额入账。余额不够时写下终态并停下，余额不为负。
     *
     * @param run 这一轮
     * @param billedTokens 本轮累计 token
     * @param reconcile 结束时是否把多扣的退回
     */
    private void apply(ChatRun run, int billedTokens, boolean reconcile) {
        BigDecimal target = owed(billedTokens, tokensPerCredit);
        BigDecimal already = alreadyCharged(run.runId());
        BigDecimal delta = target.subtract(already);
        if (delta.signum() == 0) {
            return;
        }
        if (delta.signum() < 0) {
            if (reconcile) {
                refund(run, delta.negate());
            }
            return;
        }
        BigDecimal available = available(run.userId());
        if (available.compareTo(delta) < 0) {
            events.append(run.runId(), "credit_exhausted", "{\"runId\":\"" + run.runId() + "\",\"reason\":\"INSUFFICIENT_USAGE\"}");
            events.expire(run.runId());
            throw new AgentAborted();
        }
        consume(run, delta, billedTokens);
    }

    /**
     * 读本轮累计 token。优先用累计字段。
     *
     * @param data 用量 JSON
     * @return 累计 token
     */
    private int readBilled(String data) {
        try {
            JsonNode node = json.readTree(data);
            if (node.hasNonNull("totalTokens")) {
                return node.get("totalTokens").asInt();
            }
            return node.path("promptTokens").asInt() + node.path("completionTokens").asInt();
        } catch (Exception ex) {
            return 0;
        }
    }

    /**
     * 本轮已经扣掉的净额。
     *
     * @param runId 这一轮的编号
     * @return 已扣金额
     */
    private BigDecimal alreadyCharged(String runId) {
        BigDecimal net = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (CreditLedger row : ledgers.selectList(new LambdaQueryWrapper<CreditLedger>().eq(CreditLedger::getRunId, runId))) {
            if (row.getAmount() == null || row.getType() == null) {
                continue;
            }
            if (row.getType() == CreditLedgerType.CONSUME) {
                net = net.add(row.getAmount());
            } else if (row.getType() == CreditLedgerType.REFUND) {
                net = net.subtract(row.getAmount());
            }
        }
        return net;
    }

    /**
     * 扣掉本次增量并记流水。
     *
     * @param run 这一轮
     * @param delta 本次实扣
     * @param billedTokens 本轮累计 token
     */
    private void consume(ChatRun run, BigDecimal delta, int billedTokens) {
        CreditAccount account = loadAccount(run.userId());
        BigDecimal next = available(run.userId()).subtract(delta);
        account.setAvailable(CreditPurchaseServiceImpl.money(next));
        account.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        accounts.updateById(account);
        insert(run, CreditLedgerType.CONSUME, delta, next, billedTokens);
    }

    /**
     * 结束时退回多扣的部分。
     *
     * @param run 这一轮
     * @param amount 退回金额
     */
    private void refund(ChatRun run, BigDecimal amount) {
        CreditAccount account = loadAccount(run.userId());
        if (account == null) {
            return;
        }
        BigDecimal next = available(run.userId()).add(amount);
        account.setAvailable(CreditPurchaseServiceImpl.money(next));
        account.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        accounts.updateById(account);
        insert(run, CreditLedgerType.REFUND, amount, next, billedByRun.getOrDefault(run.runId(), 0));
    }

    /**
     * 写一条积分流水。
     *
     * @param run 这一轮
     * @param type 流水类型
     * @param amount 金额
     * @param balanceAfter 变动后余额
     * @param billedTokens 本轮累计 token
     */
    private void insert(ChatRun run, CreditLedgerType type, BigDecimal amount, BigDecimal balanceAfter, int billedTokens) {
        CreditLedger ledger = new CreditLedger();
        ledger.setId(Ids.ulid());
        ledger.setUserId(run.userId());
        ledger.setType(type);
        ledger.setAmount(CreditPurchaseServiceImpl.money(amount));
        ledger.setBalanceAfter(CreditPurchaseServiceImpl.money(balanceAfter));
        ledger.setRunId(run.runId());
        ledger.setBilledTokens(billedTokens);
        ledger.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        ledgers.insert(ledger);
    }
}
