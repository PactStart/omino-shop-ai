package com.omino.shopai.app.order;

import com.omino.shopai.app.util.UnionMaps;
import com.omino.shopai.dto.order.IncomingOrderDto;
import com.omino.shopai.entity.AppOrder;
import com.omino.shopai.enums.OrderStatus;
import com.omino.shopai.exception.PlatformCallException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderSyncCatchUpTest {

    @Test
    void jdCatchesUpInOneHourSlices() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-04T14:00:00Z"), ZoneOffset.UTC);
        OffsetDateTime now = OffsetDateTime.now(clock);
        MemoryOrders orders = new MemoryOrders();
        orders.seedCursor("jd", now.minusHours(3), now.minusHours(3));
        List<UnionMaps.Window> created = new ArrayList<>();
        MemoryOrderSync.Pull source = (platform, cursor, create, update) -> {
            created.add(create);
            return List.of();
        };
        new MemoryOrderSync(orders, source, clock, "0.80").sync("jd");
        assertEquals(3, created.size());
        assertEquals(now.minusHours(3), created.get(0).start());
        assertEquals(now.minusHours(2), created.get(0).end());
        assertEquals(now, created.get(2).end());
        assertEquals(now, orders.cursor("jd").getLastSyncCreateTime());
        assertEquals(now, orders.cursor("jd").getLastSyncUpdateTime());
        assertNull(orders.cursor("jd").getLastError());
    }

    @Test
    void taobaoUsesThreeHourSlices() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-04T14:00:00Z"), ZoneOffset.UTC);
        OffsetDateTime now = OffsetDateTime.now(clock);
        MemoryOrders orders = new MemoryOrders();
        orders.seedCursor("taobao", now.minusHours(6), now.minusHours(6));
        List<UnionMaps.Window> created = new ArrayList<>();
        MemoryOrderSync.Pull source = (platform, cursor, create, update) -> {
            created.add(create);
            return List.of();
        };
        new MemoryOrderSync(orders, source, clock, "0.80").sync("taobao");
        assertEquals(2, created.size());
        assertEquals(now.minusHours(3), created.get(0).end());
        assertEquals(now, created.get(1).end());
    }

    @Test
    void emptyCursorOnlyLooksBackOneHour() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-04T14:00:00Z"), ZoneOffset.UTC);
        OffsetDateTime now = OffsetDateTime.now(clock);
        MemoryOrders orders = new MemoryOrders();
        List<UnionMaps.Window> created = new ArrayList<>();
        MemoryOrderSync.Pull source = (platform, cursor, create, update) -> {
            created.add(create);
            return List.of();
        };
        new MemoryOrderSync(orders, source, clock, "0.80").sync("jd");
        assertEquals(1, created.size());
        assertEquals(now.minusHours(1), created.get(0).start());
        assertEquals(now, created.get(0).end());
    }

    @Test
    void failureKeepsTheSliceThatAlreadyLanded() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-04T14:00:00Z"), ZoneOffset.UTC);
        OffsetDateTime now = OffsetDateTime.now(clock);
        MemoryOrders orders = new MemoryOrders();
        orders.seedCursor("jd", now.minusHours(3), now.minusHours(3));
        MemoryOrderSync.Pull source = (platform, cursor, create, update) -> {
            if (create.start().equals(now.minusHours(2))) {
                throw new PlatformCallException("查询时间范围超过 1 小时");
            }
            return List.of();
        };
        MemoryOrderSync sync = new MemoryOrderSync(orders, source, clock, "0.80");
        assertThrows(PlatformCallException.class, () -> sync.sync("jd"));
        assertEquals(now.minusHours(2), orders.cursor("jd").getLastSyncCreateTime());

        sync.submit("jd");
        assertEquals("查询时间范围超过 1 小时", orders.cursor("jd").getLastError());
        assertEquals(now.minusHours(2), orders.cursor("jd").getLastSyncCreateTime());
    }

    @Test
    void failureStoresTheInnerReasonWithoutSecrets() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-04T14:00:00Z"), ZoneOffset.UTC);
        MemoryOrders orders = new MemoryOrders();
        String secret = "app_secret=top-secret";
        MemoryOrderSync.Pull source = (platform, cursor, create, update) -> {
            throw new RuntimeException(secret, new IllegalStateException("value too long for type character(26)"));
        };
        new MemoryOrderSync(orders, source, clock, "0.80").submit("taobao");
        String error = orders.cursor("taobao").getLastError();
        assertEquals("value too long for type character(26)", error);
        assertFalse(error.contains("top-secret"));
    }

    @Test
    void platformItemIdStaysOutOfTheInternalGoodsId() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-04T14:00:00Z"), ZoneOffset.UTC);
        MemoryOrders orders = new MemoryOrders();
        String itemId = "gkt2U2v7wko5XfkXiatr-02XQW48SBANaDBGdHxz";
        OffsetDateTime paidAt = OffsetDateTime.parse("2026-11-04T12:00:00+08:00");
        OffsetDateTime completedAt = OffsetDateTime.parse("2026-11-04T13:00:00+08:00");
        IncomingOrderDto incoming = new IncomingOrderDto(
                "3317160864126018693",
                null,
                null,
                itemId,
                "商品",
                null,
                new BigDecimal("102.66"),
                new BigDecimal("3.08"),
                OrderStatus.PENDING_SHIP,
                OffsetDateTime.now(clock),
                null,
                paidAt,
                completedAt,
                " {\"payMonth\":\"202611\"} ",
                null);
        MemoryOrderSync.Pull source = (platform, cursor, create, update) -> List.of(incoming);
        MemoryOrderSync sync = new MemoryOrderSync(orders, source, clock, "0.80");
        sync.sync("taobao");
        AppOrder saved = orders.findByPlatformOrder("taobao", "3317160864126018693");
        assertNull(saved.getGoodsId());
        assertEquals(itemId, saved.getPlatformGoodsId());

        saved.setGoodsId("01M69DC83P322ETQS9QEPTBBD0");
        orders.save(saved);
        orders.seedCursor("taobao", OffsetDateTime.now(clock).minusHours(1), OffsetDateTime.now(clock).minusHours(1));
        sync.sync("taobao");
        AppOrder again = orders.findByPlatformOrder("taobao", "3317160864126018693");
        assertEquals("01M69DC83P322ETQS9QEPTBBD0", again.getGoodsId());
        assertEquals(itemId, again.getPlatformGoodsId());
        assertEquals(paidAt, again.getPaidAt());
        assertEquals(completedAt, again.getCompletedAt());
        assertEquals("{\"payMonth\":\"202611\"}", again.getExtras());
    }

    @Test
    void relationIdFillsUserAndInviterOnce() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-04T14:00:00Z"), ZoneOffset.UTC);
        MemoryOrders orders = new MemoryOrders();
        IncomingOrderDto incoming = new IncomingOrderDto(
                "PDD1",
                null,
                null,
                "goods-1",
                "商品",
                null,
                new BigDecimal("10.00"),
                new BigDecimal("1.00"),
                OrderStatus.PENDING_SHIP,
                OffsetDateTime.now(clock),
                null,
                null,
                null,
                null,
                "jd-pid-1");
        MemoryOrderSync sync = new MemoryOrderSync(orders, (platform, cursor, create, update) -> List.of(incoming), clock, "0.80");
        sync.relations(java.util.Map.of("jd-pid-1", "user-1"));
        sync.inviters(java.util.Map.of("user-1", "parent-1"));
        sync.sync("pdd");
        AppOrder saved = orders.findByPlatformOrder("pdd", "PDD1");
        assertEquals("user-1", saved.getUserId());
        assertEquals("parent-1", saved.getParentUserId());
        assertEquals("jd-pid-1", saved.getRelationId());

        orders.seedCursor("pdd", OffsetDateTime.now(clock).minusHours(1), OffsetDateTime.now(clock).minusHours(1));
        sync.inviters(java.util.Map.of("user-1", "parent-2"));
        sync.sync("pdd");
        AppOrder again = orders.findByPlatformOrder("pdd", "PDD1");
        assertEquals("user-1", again.getUserId());
        assertEquals("parent-1", again.getParentUserId());
    }
}
