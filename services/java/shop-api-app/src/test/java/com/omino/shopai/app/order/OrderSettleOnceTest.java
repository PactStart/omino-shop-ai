package com.omino.shopai.app.order;

import com.omino.shopai.enums.OrderStatus;
import com.omino.shopai.enums.SettleStatus;
import com.omino.shopai.enums.WalletLedgerType;
import com.omino.shopai.api.ApiExceptionHandler;
import com.omino.shopai.app.job.OrderSyncJob;
import com.omino.shopai.app.adapter.platform.IPlatformOrder;
import com.omino.shopai.app.service.order.EmptyPlatformOrderImpl;
import com.omino.shopai.dto.order.IncomingOrderDto;
import com.omino.shopai.app.service.order.OrderQueryService;
import com.omino.shopai.app.service.order.OrderSyncService;
import com.omino.shopai.app.web.app.OrderController;
import com.omino.shopai.entity.AppOrder;
import com.omino.shopai.entity.OrderSyncCursor;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.WalletLedger;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderSettleOnceTest {

    private static final String BUYER = "01BUYER000000000000000000A";
    private static final String PARENT = "01PARENT00000000000000000B";

    @Test
    void emptyJobDoesNotCredit() {
        MemoryOrders orders = new MemoryOrders();
        new OrderSyncJob(sync(orders, new EmptyPlatformOrderImpl(), "0.80")).run();
        assertEquals(0, orders.commissionCount(BUYER));
        assertNotNull(orders.cursor("jd").getLastSyncUpdateTime());
        assertNotNull(orders.cursor("taobao").getLastSyncUpdateTime());
    }

    @Test
    void completedOrderCreditsBuyerOnceAndHidesMediaCommission() throws Exception {
        MemoryOrders orders = new MemoryOrders();
        orders.seedWallet(BUYER);
        IPlatformOrder source = (platform, cursor, created, updated) -> List.of(sample());
        OrderSyncService sync = sync(orders, source, "0.80");

        orders.failAdvance = true;
        assertThrows(IllegalStateException.class, () -> sync.sync("jd"));
        assertNull(orders.findByPlatformOrder("jd", "JD100"));
        assertNull(orders.cursor("jd").getLastSyncUpdateTime());
        assertEquals(0, orders.commissionCount(BUYER));
        assertEquals(0, new BigDecimal("0.00").compareTo(orders.lockWallet(BUYER).getAvailableAmount()));

        orders.failAdvance = false;
        sync.sync("jd");
        AppOrder stored = orders.findByPlatformOrder("jd", "JD100");
        assertEquals(PARENT, stored.getParentUserId());
        assertEquals(0, new BigDecimal("100.00").compareTo(stored.getMediaCommission()));
        assertEquals(0, new BigDecimal("80.00").compareTo(stored.getUserCommission()));
        assertEquals(OrderStatus.COMPLETED, stored.getOrderStatus());
        assertEquals(SettleStatus.SETTLED, stored.getSettleStatus());
        assertNotNull(stored.getWalletLedgerId());
        List<WalletLedger> buyerLedgers = orders.ledgers(BUYER);
        assertEquals(1, buyerLedgers.size());
        assertEquals(WalletLedgerType.COMMISSION, buyerLedgers.get(0).getType());
        assertEquals(stored.getId(), buyerLedgers.get(0).getOrderId());
        assertEquals(0, orders.ledgers(PARENT).size());
        Wallet wallet = orders.lockWallet(BUYER);
        assertEquals(0, new BigDecimal("80.00").compareTo(wallet.getAvailableAmount()));
        assertEquals(0, new BigDecimal("80.00").compareTo(wallet.getTotalEarned()));
        assertEquals(0, new BigDecimal("0.00").compareTo(wallet.getFrozenAmount()));

        sync(orders, source, "0.50").sync("jd");
        AppOrder again = orders.findByPlatformOrder("jd", "JD100");
        assertEquals(0, new BigDecimal("80.00").compareTo(again.getUserCommission()));
        assertEquals(stored.getWalletLedgerId(), again.getWalletLedgerId());
        assertEquals(1, orders.ledgers(BUYER).size());
        assertEquals(0, new BigDecimal("80.00").compareTo(orders.lockWallet(BUYER).getAvailableAmount()));
        assertNotNull(orders.cursor("jd").getLastSyncUpdateTime());

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new OrderController(new OrderQueryService(orders)))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        MvcResult list = mvc.perform(get("/v1/orders").requestAttr("userId", BUYER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].userCommission").value("80.00"))
                .andExpect(jsonPath("$.data.list[0].settleStatus").value("settled"))
                .andExpect(jsonPath("$.data.list[0].mediaCommission").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].parentUserId").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].walletLedgerId").doesNotExist())
                .andReturn();
        assertHidden(list.getResponse().getContentAsString());
        MvcResult detail = mvc.perform(get("/v1/orders/" + stored.getId()).requestAttr("userId", BUYER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userCommission").value("80.00"))
                .andExpect(jsonPath("$.data.logistics").value(nullValue()))
                .andExpect(jsonPath("$.data.mediaCommission").doesNotExist())
                .andExpect(jsonPath("$.data.parentUserId").doesNotExist())
                .andExpect(jsonPath("$.data.walletLedgerId").doesNotExist())
                .andReturn();
        assertHidden(detail.getResponse().getContentAsString());
    }

    private static void assertHidden(String body) {
        assertFalse(body.contains("mediaCommission"));
        assertFalse(body.contains("parentUserId"));
        assertFalse(body.contains("walletLedgerId"));
        assertFalse(body.contains(PARENT));
        assertFalse(body.contains("100.00"));
    }

    private static OrderSyncService sync(MemoryOrders orders, IPlatformOrder source, String rate) {
        return new OrderSyncService(orders, source, orders, Clock.systemUTC(), rate);
    }

    private static IncomingOrderDto sample() {
        return new IncomingOrderDto(
                "JD100",
                BUYER,
                PARENT,
                null,
                "降噪耳机",
                "https://cdn.example/1.jpg",
                new BigDecimal("199.00"),
                new BigDecimal("100.00"),
                OrderStatus.COMPLETED,
                OffsetDateTime.parse("2026-10-29T10:00:00+08:00"),
                OffsetDateTime.parse("2026-10-29T12:00:00+08:00"));
    }
}
