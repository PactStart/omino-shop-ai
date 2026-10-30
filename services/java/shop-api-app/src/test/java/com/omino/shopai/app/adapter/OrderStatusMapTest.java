package com.omino.shopai.app.adapter;

import com.omino.shopai.app.adapter.platform.UnionMaps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderStatusMapTest {

    @Test
    void followsReferenceStatusTable() {
        assertEquals("pending_ship", UnionMaps.taobaoStatus(12L));
        assertEquals("completed", UnionMaps.taobaoStatus(3L));
        assertEquals("completed", UnionMaps.taobaoStatus(14L));
        assertEquals("cancelled", UnionMaps.taobaoStatus(13L));
        assertEquals("pending_pay", UnionMaps.taobaoStatus(null));
        assertEquals("pending_pay", UnionMaps.taobaoStatus(99L));

        assertEquals("pending_pay", UnionMaps.jdStatus(15));
        assertEquals("pending_ship", UnionMaps.jdStatus(16));
        assertEquals("completed", UnionMaps.jdStatus(17));
        assertEquals("pending_ship", UnionMaps.jdStatus(24));
        assertEquals("pending_pay", UnionMaps.jdStatus(3));
        assertEquals("pending_pay", UnionMaps.jdStatus(null));

        assertEquals("pending_ship", UnionMaps.pddStatus(0));
        assertEquals("shipped", UnionMaps.pddStatus(1));
        assertEquals("shipped", UnionMaps.pddStatus(2));
        assertEquals("completed", UnionMaps.pddStatus(3));
        assertEquals("cancelled", UnionMaps.pddStatus(4));
        assertEquals("completed", UnionMaps.pddStatus(5));
        assertEquals("cancelled", UnionMaps.pddStatus(10));
        assertEquals("pending_pay", UnionMaps.pddStatus(8));
        assertEquals("pending_pay", UnionMaps.pddStatus(null));

        assertEquals("cancelled", UnionMaps.vipStatus((short) 0));
        assertEquals("pending_pay", UnionMaps.vipStatus((short) 1));
        assertEquals("completed", UnionMaps.vipStatus((short) 2));
        assertEquals("pending_pay", UnionMaps.vipStatus((short) 6));
        assertEquals("pending_pay", UnionMaps.vipStatus(null));
    }
}
