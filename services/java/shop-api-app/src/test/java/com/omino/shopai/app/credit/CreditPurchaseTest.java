package com.omino.shopai.app.credit;

import com.omino.shopai.enums.CreditOrderStatus;
import com.omino.shopai.app.adapter.pay.AlipayNotifyImpl;
import com.omino.shopai.app.adapter.pay.WechatPayNotifyImpl;
import com.omino.shopai.app.service.credit.ICreditPurchaseService;
import com.omino.shopai.entity.CreditOrder;
import com.omino.shopai.entity.CreditPackage;
import com.omino.shopai.entity.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreditPurchaseTest {

    private static final String USER = "01USER0000000000000000000A";

    private MemoryCredits books;
    private ICreditPurchaseService service;

    @BeforeEach
    void setUp() {
        books = new MemoryCredits();
        books.seed(USER, "10.00");
        service = new MemoryPurchase(books, Clock.systemUTC());
    }

    @Test
    void walletPurchaseDebitsAndCreditsUnderTheSameLock() {
        CreditPackage pack = books.addPackage("4.00", "5.00");
        CreditOrder order = service.place(USER, pack.getId(), "wallet", "client-1");
        assertEquals(CreditOrderStatus.PAID, order.getStatus());
        Wallet wallet = books.wallet(USER);
        assertEquals(0, new BigDecimal("6.00").compareTo(wallet.getAvailableAmount()));
        assertEquals(0, new BigDecimal("5.00").compareTo(books.available(USER)));
        assertEquals(1, books.walletLedgerCount("credit_buy"));
        assertTrue(books.purchaseExists(order.getId()));
    }

    @Test
    void interleavedWithWithdrawalStaysNonNegative() throws Exception {
        CreditPackage pack = books.addPackage("10.00", "8.00");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> buy = pool.submit(() -> {
                start.await();
                service.place(USER, pack.getId(), "wallet", "client-race");
                return true;
            });
            Future<Boolean> withdraw = pool.submit(() -> {
                start.await();
                books.freeze(USER, "10.00");
                return true;
            });
            start.countDown();
            int wins = outcome(buy) + outcome(withdraw);
            assertEquals(1, wins);
            Wallet wallet = books.wallet(USER);
            assertTrue(wallet.getAvailableAmount().signum() >= 0);
            assertTrue(wallet.getFrozenAmount().signum() >= 0);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void badSignatureDoesNotCreditAndValidCallbackCreditsOnce() {
        assertFalse(new WechatPayNotifyImpl("", "", "").accepted("serial", "1", "n", "sig", "{}"));
        assertFalse(new AlipayNotifyImpl("").accepted("out_trade_no=x&sign=y"));
        CreditPackage pack = books.addPackage("1.00", "3.00");
        CreditOrder wechat = service.place(USER, pack.getId(), "wechat_pay", "wx-1");
        CreditOrder alipay = service.place(USER, pack.getId(), "alipay", "ali-1");
        assertEquals(CreditOrderStatus.PENDING, wechat.getStatus());
        assertFalse(service.settle(wechat.getId(), "txn-w", false));
        assertFalse(service.settle(alipay.getId(), "txn-a", false));
        assertEquals(0, BigDecimal.ZERO.compareTo(books.available(USER)));
        assertFalse(books.purchaseExists(wechat.getId()));
        assertTrue(service.settle(wechat.getId(), "txn-w", true));
        assertTrue(service.settle(wechat.getId(), "txn-w", true));
        assertTrue(service.settle(alipay.getId(), "txn-a", true));
        assertTrue(service.settle(alipay.getId(), "txn-a", true));
        assertTrue(books.purchaseExists(wechat.getId()));
        assertTrue(books.purchaseExists(alipay.getId()));
        assertEquals(1, books.ledger(USER).stream().filter(row -> wechat.getId().equals(row.getCreditOrderId())).count());
        assertEquals(1, books.ledger(USER).stream().filter(row -> alipay.getId().equals(row.getCreditOrderId())).count());
        assertEquals(0, new BigDecimal("6.00").compareTo(books.available(USER)));
        assertEquals(0, new BigDecimal("10.00").compareTo(books.wallet(USER).getAvailableAmount()));
    }

    private static int outcome(Future<Boolean> future) {
        try {
            future.get(5, TimeUnit.SECONDS);
            return 1;
        } catch (Exception ex) {
            return 0;
        }
    }
}
