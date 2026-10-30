package com.omino.shopai.app.adapter;

import com.omino.shopai.app.adapter.platform.CountMaps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SalesCountMappingTest {

    @Test
    void missingOrUnparseableCountsStayNullAndZeroIsKept() {
        assertNull(CountMaps.sales(null));
        assertNull(CountMaps.sales(""));
        assertNull(CountMaps.sales("  "));
        assertNull(CountMaps.sales("1万+"));
        assertNull(CountMaps.sales("1.5"));
        assertEquals(0L, CountMaps.sales(0));
        assertEquals(0L, CountMaps.sales("0"));
        assertEquals(12L, CountMaps.sales("12"));

        assertNull(CountMaps.reviews(null));
        assertNull(CountMaps.reviews("好评"));
        assertEquals(0, CountMaps.reviews(0));
        assertEquals(0, CountMaps.reviews("0"));
        assertEquals(8, CountMaps.reviews(8L));
    }
}
