package com.omino.shopai.app.adapter;

import com.omino.shopai.app.util.UnionMaps;
import com.omino.shopai.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderStatusMapTest {

    @Test
    void followsReferenceStatusTable() {
        assertEquals(OrderStatus.PENDING_SHIP, UnionMaps.taobaoStatus(12L));
        assertEquals(OrderStatus.COMPLETED, UnionMaps.taobaoStatus(3L));
        assertEquals(OrderStatus.COMPLETED, UnionMaps.taobaoStatus(14L));
        assertEquals(OrderStatus.CANCELLED, UnionMaps.taobaoStatus(13L));
        assertEquals(OrderStatus.PENDING_PAY, UnionMaps.taobaoStatus(null));
        assertEquals(OrderStatus.PENDING_PAY, UnionMaps.taobaoStatus(99L));

        assertEquals(OrderStatus.PENDING_PAY, UnionMaps.jdStatus(15));
        assertEquals(OrderStatus.PENDING_SHIP, UnionMaps.jdStatus(16));
        assertEquals(OrderStatus.COMPLETED, UnionMaps.jdStatus(17));
        assertEquals(OrderStatus.PENDING_SHIP, UnionMaps.jdStatus(24));
        assertEquals(OrderStatus.PENDING_PAY, UnionMaps.jdStatus(3));
        assertEquals(OrderStatus.PENDING_PAY, UnionMaps.jdStatus(null));

        assertEquals(OrderStatus.PENDING_SHIP, UnionMaps.pddStatus(0));
        assertEquals(OrderStatus.SHIPPED, UnionMaps.pddStatus(1));
        assertEquals(OrderStatus.SHIPPED, UnionMaps.pddStatus(2));
        assertEquals(OrderStatus.COMPLETED, UnionMaps.pddStatus(3));
        assertEquals(OrderStatus.CANCELLED, UnionMaps.pddStatus(4));
        assertEquals(OrderStatus.COMPLETED, UnionMaps.pddStatus(5));
        assertEquals(OrderStatus.CANCELLED, UnionMaps.pddStatus(10));
        assertEquals(OrderStatus.PENDING_PAY, UnionMaps.pddStatus(8));
        assertEquals(OrderStatus.PENDING_PAY, UnionMaps.pddStatus(null));

        assertEquals(OrderStatus.CANCELLED, UnionMaps.vipStatus((short) 0));
        assertEquals(OrderStatus.PENDING_PAY, UnionMaps.vipStatus((short) 1));
        assertEquals(OrderStatus.COMPLETED, UnionMaps.vipStatus((short) 2));
        assertEquals(OrderStatus.PENDING_PAY, UnionMaps.vipStatus((short) 6));
        assertEquals(OrderStatus.PENDING_PAY, UnionMaps.vipStatus(null));
    }
}
