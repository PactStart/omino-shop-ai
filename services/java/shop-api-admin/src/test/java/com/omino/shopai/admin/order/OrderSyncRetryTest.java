package com.omino.shopai.admin.order;

import com.omino.shopai.admin.service.order.AdminOrderSyncService;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.AdminAuditLog;
import com.omino.shopai.exception.ApiBusinessException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
