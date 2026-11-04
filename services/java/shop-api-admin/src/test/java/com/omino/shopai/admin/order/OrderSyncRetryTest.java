package com.omino.shopai.admin.order;

import com.omino.shopai.admin.service.order.AdminOrderSyncService;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.AdminAuditLog;
import com.omino.shopai.entity.OrderSyncCursor;
import com.omino.shopai.enums.Platform;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.OrderSyncCursorMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderSyncRetryTest {

    @Test
    void unknownPlatformIsRejectedBeforeTheAppIsCalled() {
        List<String> calls = new ArrayList<>();
        List<AdminAuditLog> audits = new ArrayList<>();
        AdminOrderSyncService service = new AdminOrderSyncService(null, audits::add, calls::add);
        ApiBusinessException rejected = assertThrows(ApiBusinessException.class, () -> service.retry("meituan", "ops"));
        assertEquals(ErrorCodes.VALIDATION, rejected.code());
        assertTrue(calls.isEmpty());
        assertTrue(audits.isEmpty());
    }

    @Test
    void retryAuditsThePlatformWithoutSecrets() {
        List<String> calls = new ArrayList<>();
        List<AdminAuditLog> audits = new ArrayList<>();
        AdminOrderSyncService service = new AdminOrderSyncService(null, audits::add, calls::add);
        service.retry("jd", "ops");
        assertEquals(List.of("jd"), calls);
        assertEquals("order.sync.retry", audits.get(0).getAction());
        String detail = audits.get(0).getDetail();
        assertTrue(detail == null || (!detail.toLowerCase().contains("secret")
                && !detail.toLowerCase().contains("pid")
                && !detail.matches(".*\\d{8,}.*")));
    }

    @Test
    void saveCursorRejectsUnknownPlatformBeforeWriting() {
        OrderSyncCursorMapper cursors = mock(OrderSyncCursorMapper.class);
        List<AdminAuditLog> audits = new ArrayList<>();
        AdminOrderSyncService service = new AdminOrderSyncService(cursors, audits::add, ignored -> { });
        ApiBusinessException rejected = assertThrows(ApiBusinessException.class,
                () -> service.saveCursor("meituan", "2026-11-01T00:00:00Z", null, "ops"));
        assertEquals(ErrorCodes.VALIDATION, rejected.code());
        assertTrue(audits.isEmpty());
        verifyNoInteractions(cursors);
    }

    @Test
    void saveCursorInsertsMissingRowAndAuditsTheNewTimes() {
        OrderSyncCursorMapper cursors = mock(OrderSyncCursorMapper.class);
        when(cursors.update(isNull(), any())).thenReturn(0);
        when(cursors.insert(any(OrderSyncCursor.class))).thenAnswer(invocation -> {
            OrderSyncCursor row = invocation.getArgument(0);
            assertEquals(Platform.JD, row.getPlatform());
            assertNotNull(row.getLastSyncCreateTime());
            assertNull(row.getLastSyncUpdateTime());
            assertNotNull(row.getUpdatedAt());
            return 1;
        });
        List<AdminAuditLog> audits = new ArrayList<>();
        AdminOrderSyncService service = new AdminOrderSyncService(cursors, audits::add, ignored -> { });
        service.saveCursor("jd", "2026-11-01T00:00:00Z", " ", "ops");
        assertEquals("order.sync.cursor", audits.get(0).getAction());
        assertEquals("jd", audits.get(0).getResource());
        assertTrue(audits.get(0).getDetail().contains("2026-11-01T00:00Z"));
        assertTrue(audits.get(0).getDetail().contains("\"lastSyncUpdateTime\":null"));
        assertFalse(audits.get(0).getDetail().toLowerCase().contains("secret"));
    }

    @Test
    void saveCursorRejectsATimeWithoutAZone() {
        OrderSyncCursorMapper cursors = mock(OrderSyncCursorMapper.class);
        List<AdminAuditLog> audits = new ArrayList<>();
        AdminOrderSyncService service = new AdminOrderSyncService(cursors, audits::add, ignored -> { });
        ApiBusinessException rejected = assertThrows(ApiBusinessException.class,
                () -> service.saveCursor("jd", "2026-11-01 00:00:00", null, "ops"));
        assertEquals(ErrorCodes.VALIDATION, rejected.code());
        assertTrue(audits.isEmpty());
        verifyNoInteractions(cursors);
    }
}
