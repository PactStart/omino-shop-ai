package com.omino.shopai.support.withdrawal;

import com.omino.shopai.enums.WithdrawalStatus;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.Wallet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WithdrawalTransitionTest {

    @Test
    void freezeMovesAvailableIntoFrozenAndWritesNoDecisionToDeduct() {
        Wallet wallet = wallet("10.00", "0.00");
        WalletMath.freeze(wallet, new BigDecimal("4.00"), OffsetDateTime.now(ZoneOffset.UTC));
        assertEquals(0, new BigDecimal("6.00").compareTo(wallet.getAvailableAmount()));
        assertEquals(0, new BigDecimal("4.00").compareTo(wallet.getFrozenAmount()));
        assertThrows(IllegalStateException.class, () -> WalletMath.freeze(wallet, new BigDecimal("7.00"), OffsetDateTime.now(ZoneOffset.UTC)));
        assertEquals(0, new BigDecimal("6.00").compareTo(wallet.getAvailableAmount()));
        assertEquals(0, new BigDecimal("4.00").compareTo(wallet.getFrozenAmount()));
    }

    @Test
    void failedPayoutKeepsPendingAndDoesNotWriteATransferId() {
        WithdrawalTransition.Decision decision = WithdrawalTransition.afterPayout(WithdrawalStatus.PENDING, false, "ch_should_not_stick");
        assertEquals(WithdrawalStatus.PENDING, decision.status());
        assertEquals(WithdrawalTransition.FAIL_MESSAGE, decision.message());
        assertEquals("failed", decision.payoutResult());
        assertFalse(decision.deductFrozen());
        assertFalse(decision.writeLedger());
        assertNull(decision.providerTransferId());
    }

    @Test
    void successfulPayoutPaysOnceAndALaterCallDoesNot() {
        WithdrawalTransition.Decision paid = WithdrawalTransition.afterPayout(WithdrawalStatus.PENDING, true, "ch_1");
        assertEquals(WithdrawalStatus.PAID, paid.status());
        assertTrue(paid.deductFrozen());
        assertTrue(paid.writeLedger());
        assertEquals("ch_1", paid.providerTransferId());
        assertTrue(WithdrawalTransition.shouldCallPort(WithdrawalStatus.PENDING));
        assertFalse(WithdrawalTransition.shouldCallPort(WithdrawalStatus.PAID));
        WithdrawalTransition.Decision again = WithdrawalTransition.afterPayout(WithdrawalStatus.PAID, true, "ch_2");
        assertEquals(WithdrawalStatus.PAID, again.status());
        assertFalse(again.deductFrozen());
        assertFalse(again.writeLedger());
        assertNull(again.providerTransferId());
    }

    @Test
    void rejectUnfreezesOnlyWhilePending() {
        WithdrawalTransition.Decision decision = WithdrawalTransition.reject(WithdrawalStatus.PENDING);
        assertEquals(WithdrawalStatus.REJECTED, decision.status());
        assertTrue(decision.unfreeze());
        assertFalse(decision.writeLedger());
        WithdrawalTransition.Decision again = WithdrawalTransition.reject(WithdrawalStatus.REJECTED);
        assertEquals(WithdrawalStatus.REJECTED, again.status());
        assertFalse(again.unfreeze());
    }

    @Test
    void channelOtherThanAlipayIs40001() {
        ApiBusinessException ex = assertThrows(ApiBusinessException.class, () -> WithdrawalTransition.requireAlipay("wechat"));
        assertEquals(ErrorCodes.VALIDATION, ex.code());
        WithdrawalTransition.requireAlipay(null);
        WithdrawalTransition.requireAlipay("alipay");
    }

    private static Wallet wallet(String available, String frozen) {
        Wallet wallet = new Wallet();
        wallet.setAvailableAmount(new BigDecimal(available));
        wallet.setFrozenAmount(new BigDecimal(frozen));
        wallet.setTotalEarned(BigDecimal.ZERO);
        return wallet;
    }
}
