package com.omino.shopai.app.credit;

import com.omino.shopai.app.service.credit.CreditBillingImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CreditUsageTest {

    private static final BigDecimal RATE = new BigDecimal("1000");

    @Test
    void cumulativeTokensSettleOnce() {
        BigDecimal first = CreditBillingImpl.owed(100, RATE);
        BigDecimal second = CreditBillingImpl.owed(150, RATE);
        assertEquals(0, second.compareTo(new BigDecimal("0.15")));
        assertEquals(0, CreditBillingImpl.increment(second, first).compareTo(new BigDecimal("0.05")));
        assertEquals(0, CreditBillingImpl.increment(second, second).compareTo(new BigDecimal("0.00")));
    }

    @Test
    void floorAppliesToTheRunTotal() {
        BigDecimal first = CreditBillingImpl.owed(1, RATE);
        BigDecimal second = CreditBillingImpl.owed(2, RATE);
        assertEquals(0, first.compareTo(new BigDecimal("0.01")));
        assertEquals(0, second.compareTo(new BigDecimal("0.01")));
        assertEquals(0, CreditBillingImpl.increment(second, first).compareTo(new BigDecimal("0.00")));
    }
}
