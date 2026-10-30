package com.omino.shopai.app.order;

import com.omino.shopai.app.adapter.platform.JdGoodsClient;
import com.omino.shopai.app.adapter.platform.PddGoodsClient;
import com.omino.shopai.app.adapter.platform.PlatformCallException;
import com.omino.shopai.app.adapter.platform.PlatformOrderSource;
import com.omino.shopai.app.adapter.platform.SdkPlatformOrderSource;
import com.omino.shopai.app.adapter.platform.SdkSessions;
import com.omino.shopai.app.adapter.platform.TaobaoGoodsClient;
import com.omino.shopai.app.adapter.platform.VipGoodsClient;
import com.omino.shopai.app.job.OrderSyncJob;
import com.omino.shopai.app.service.order.OrderSyncService;
import com.omino.shopai.dto.order.IncomingOrder;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderSyncMissingKeyTest {

    @Test
    void emptyKeysDoNotMoveCursorsOrOpenClients() {
        SdkSessions.opened.set(0);
        MemoryOrders orders = new MemoryOrders();
        OrderSyncService sync = new OrderSyncService(
                orders,
                new SdkPlatformOrderSource(List.of(
                        new TaobaoGoodsClient("", ""),
                        new JdGoodsClient("", ""),
                        new PddGoodsClient("", ""),
                        new VipGoodsClient("", ""))),
                orders,
                Clock.systemUTC(),
                "0.80");
        assertThrows(PlatformCallException.class, () -> sync.sync("jd"));
        assertNull(orders.cursor("jd").getLastSyncCreateTime());
        assertNull(orders.cursor("jd").getLastSyncUpdateTime());
        assertEquals(0, SdkSessions.opened.get());

        new OrderSyncJob(sync).run();
        for (String platform : List.of("taobao", "jd", "pdd", "vip")) {
            assertNull(orders.cursor(platform).getLastSyncCreateTime());
            assertNull(orders.cursor(platform).getLastSyncUpdateTime());
            assertEquals("上游失败", orders.cursor(platform).getLastError());
            assertFalse(orders.cursor(platform).getLastError().contains("pid"));
        }
        assertEquals(0, SdkSessions.opened.get());
    }

    @Test
    void onePlatformFailureDoesNotSkipTheOthers() {
        MemoryOrders orders = new MemoryOrders();
        PlatformOrderSource source = (platform, cursor) -> {
            if ("taobao".equals(platform)) {
                throw new PlatformCallException();
            }
            return List.of();
        };
        OrderSyncService sync = new OrderSyncService(orders, source, orders, Clock.systemUTC(), "0.80");
        new OrderSyncJob(sync).run();
        assertNull(orders.cursor("taobao").getLastSyncCreateTime());
        assertEquals("上游失败", orders.cursor("taobao").getLastError());
        assertEquals(null, orders.cursor("jd").getLastError());
        org.junit.jupiter.api.Assertions.assertNotNull(orders.cursor("jd").getLastSyncCreateTime());
        org.junit.jupiter.api.Assertions.assertNotNull(orders.cursor("pdd").getLastSyncUpdateTime());
        org.junit.jupiter.api.Assertions.assertNotNull(orders.cursor("vip").getLastSyncUpdateTime());
    }

    @Test
    void incomingOrderLivesOutsideTheAppServiceLayer() {
        IncomingOrder order = new IncomingOrder("1", null, null, null, null, null, null, null, "unknown", null, null);
        assertEquals("com.omino.shopai.dto.order", order.getClass().getPackageName());
    }
}
