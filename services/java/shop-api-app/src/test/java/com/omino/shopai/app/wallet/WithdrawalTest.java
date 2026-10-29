package com.omino.shopai.app.wallet;

import com.omino.shopai.enums.WithdrawalStatus;
import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ApiExceptionHandler;
import com.omino.shopai.app.service.wallet.WithdrawalService;
import com.omino.shopai.app.service.wallet.WithdrawalView;
import com.omino.shopai.app.web.app.WalletController;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.Withdrawal;
import com.omino.shopai.support.withdrawal.PayoutPort;
import com.omino.shopai.support.withdrawal.PayoutView;
import com.omino.shopai.support.withdrawal.WithdrawalTransition;
import com.omino.shopai.support.withdrawal.WithdrawalWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WithdrawalTest {

    private static final String USER = "01USER0000000000000000000A";
    private static final String OTHER = "01USER0000000000000000000B";

    private MemoryBooks books;
    private WithdrawalService service;
    private WithdrawalWorkflow workflow;
    private ScriptedPayout port;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        books = new MemoryBooks();
        books.seed(USER, "10.00");
        service = new WithdrawalService(books, Clock.systemUTC());
        port = new ScriptedPayout();
        workflow = new WithdrawalWorkflow(books, port, Clock.systemUTC());
        mvc = MockMvcBuilders.standaloneSetup(new WalletController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void applyMovesAvailableToFrozenWithoutLedger() {
        WithdrawalView view = service.apply(USER, "4.00", "13800138000", null);
        assertEquals("pending", view.status());
        assertEquals("alipay", view.channel());
        assertEquals("138****8000", view.accountMask());
        Wallet wallet = books.wallet(USER);
        assertEquals(0, new BigDecimal("6.00").compareTo(wallet.getAvailableAmount()));
        assertEquals(0, new BigDecimal("4.00").compareTo(wallet.getFrozenAmount()));
        assertEquals(0, books.ledgerCount());
        assertNull(books.find(view.id()).getProviderTransferId());
    }

    @Test
    void interleavedAppliesStayNonNegative() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!go.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("go");
                    }
                    try {
                        return service.apply(USER, "10.00", "alipay@example.com", null);
                    } catch (ApiBusinessException ex) {
                        return ex.code();
                    }
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            int ok = 0;
            int rejected = 0;
            for (Future<Object> future : futures) {
                Object value = future.get(5, TimeUnit.SECONDS);
                if (value instanceof WithdrawalView) {
                    ok++;
                } else if (Integer.valueOf(40001).equals(value)) {
                    rejected++;
                }
            }
            assertEquals(1, ok);
            assertEquals(1, rejected);
        } finally {
            pool.shutdownNow();
        }
        Wallet wallet = books.wallet(USER);
        assertTrue(wallet.getAvailableAmount().signum() >= 0);
        assertTrue(wallet.getFrozenAmount().signum() >= 0);
        assertEquals(0, new BigDecimal("0.00").compareTo(wallet.getAvailableAmount()));
        assertEquals(0, new BigDecimal("10.00").compareTo(wallet.getFrozenAmount()));
        assertEquals(0, books.ledgerCount());
    }

    @Test
    void failedPayoutKeepsFreezeUntilALaterSuccess() {
        WithdrawalView view = service.apply(USER, "10.00", "alipay@example.com", "alipay");
        books.presetTransferId(view.id(), "PRESET");
        port.succeed = false;
        PayoutView failed = workflow.payout(view.id());
        assertEquals(WithdrawalTransition.FAIL_MESSAGE, failed.message());
        assertEquals("failed", failed.payoutResult());
        assertEquals("pending", failed.status());
        Withdrawal stored = books.find(view.id());
        assertEquals(WithdrawalStatus.PENDING, stored.getStatus());
        assertEquals("PRESET", stored.getProviderTransferId());
        assertNull(stored.getPaidAt());
        assertEquals(List.of(view.id()), port.ids);
        assertEquals(0, new BigDecimal("10.00").compareTo(books.wallet(USER).getFrozenAmount()));
        assertEquals(0, books.withdrawLedgerCount(view.id()));

        port.succeed = true;
        PayoutView paid = workflow.payout(view.id());
        assertEquals("paid", paid.status());
        stored = books.find(view.id());
        assertEquals(WithdrawalStatus.PAID, stored.getStatus());
        assertEquals("ch_" + view.id(), stored.getProviderTransferId());
        assertNotNullPaid(stored);
        assertEquals(0, new BigDecimal("0.00").compareTo(books.wallet(USER).getFrozenAmount()));
        assertEquals(0, new BigDecimal("0.00").compareTo(books.wallet(USER).getAvailableAmount()));
        assertEquals(1, books.withdrawLedgerCount(view.id()));
        assertEquals(List.of(view.id(), view.id()), port.ids);

        int calls = port.ids.size();
        workflow.payout(view.id());
        assertEquals(calls, port.ids.size());
        assertEquals(1, books.withdrawLedgerCount(view.id()));
        assertEquals(WithdrawalStatus.PAID, books.find(view.id()).getStatus());
    }

    @Test
    void rereadAfterLockSkipsPayoutWhenNoLongerPending() {
        WithdrawalView view = service.apply(USER, "10.00", "alipay@example.com", null);
        books.rejectOnNextLockedRead = true;
        PayoutView skipped = workflow.payout(view.id());
        assertEquals(0, port.ids.size());
        assertEquals(WithdrawalStatus.PENDING, books.find(view.id()).getStatus());
        assertEquals("ok", skipped.message());
        assertEquals(0, new BigDecimal("10.00").compareTo(books.wallet(USER).getFrozenAmount()));
        assertEquals(0, books.ledgerCount());
    }

    @Test
    void rejectUnfreezesAndASecondRejectDoesNot() {
        WithdrawalView view = service.apply(USER, "6.00", "13800138000", null);
        workflow.reject(view.id(), "账号不对");
        assertEquals(WithdrawalStatus.REJECTED, books.find(view.id()).getStatus());
        assertEquals(0, new BigDecimal("10.00").compareTo(books.wallet(USER).getAvailableAmount()));
        assertEquals(0, new BigDecimal("0.00").compareTo(books.wallet(USER).getFrozenAmount()));
        assertEquals(0, books.ledgerCount());
        workflow.reject(view.id(), "再拒");
        assertEquals(WithdrawalStatus.REJECTED, books.find(view.id()).getStatus());
        assertEquals(0, new BigDecimal("10.00").compareTo(books.wallet(USER).getAvailableAmount()));
        assertEquals(0, new BigDecimal("0.00").compareTo(books.wallet(USER).getFrozenAmount()));
    }

    @Test
    void channelOtherThanAlipayIs40001() {
        ApiBusinessException ex = assertThrows(ApiBusinessException.class,
                () -> service.apply(USER, "1.00", "a@b.c", "wechat"));
        assertEquals(40001, ex.code());
        assertEquals(0, books.count(USER));
        assertEquals(0, new BigDecimal("10.00").compareTo(books.wallet(USER).getAvailableAmount()));
        assertEquals(0, new BigDecimal("0.00").compareTo(books.wallet(USER).getFrozenAmount()));
    }

    @Test
    void consumerResponseHidesThePayeeAccount() throws Exception {
        mvc.perform(post("/v1/withdrawals")
                        .requestAttr("userId", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"3.00\",\"payeeAccount\":\"alipay@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.accountMask").value("al****om"))
                .andExpect(jsonPath("$.data.payeeAccount").doesNotExist());
        MvcResult list = mvc.perform(get("/v1/withdrawals").requestAttr("userId", USER))
                .andExpect(jsonPath("$.data.list[0].accountMask").value("al****om"))
                .andExpect(jsonPath("$.data.list[0].payeeAccount").doesNotExist())
                .andReturn();
        assertFalse(list.getResponse().getContentAsString().contains("alipay@example.com"));
        String id = books.list(USER, 1, 20).get(0).getId();
        mvc.perform(get("/v1/withdrawals/" + id).requestAttr("userId", OTHER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
        mvc.perform(get("/v1/wallet").requestAttr("userId", USER))
                .andExpect(jsonPath("$.data.availableAmount").value("7.00"))
                .andExpect(jsonPath("$.data.frozenAmount").value("3.00"))
                .andExpect(jsonPath("$.data.totalEarned").value("0.00"));
    }

    private static void assertNotNullPaid(Withdrawal stored) {
        assertTrue(stored.getPaidAt() != null);
    }

    private static final class ScriptedPayout implements PayoutPort {
        private final List<String> ids = new ArrayList<>();
        private boolean succeed;

        @Override
        public Result transfer(String withdrawalId, BigDecimal amount, String payeeAccount) {
            ids.add(withdrawalId);
            return succeed ? Result.ok("ch_" + withdrawalId) : Result.failed();
        }
    }
}
