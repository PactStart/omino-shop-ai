package com.omino.shopai.admin.ops;

import com.omino.shopai.admin.service.ops.AdminDirectory;
import com.omino.shopai.entity.CreditPackage;
import com.omino.shopai.enums.ActiveStatus;
import com.omino.shopai.mapper.CreditPackageMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreditPackageSaveTest {

    @Test
    void insertWritesUpdatedAtBeforeTheRowIsStored() {
        CreditPackageMapper packages = mock(CreditPackageMapper.class);
        when(packages.insert(any(CreditPackage.class))).thenAnswer(invocation -> {
            CreditPackage row = invocation.getArgument(0);
            assertNotNull(row.getCreatedAt());
            assertNotNull(row.getUpdatedAt());
            return 1;
        });
        AdminDirectory directory = directory(packages);
        CreditPackage pack = new CreditPackage();
        pack.setName("基本版");
        pack.setCredits(new BigDecimal("100.00"));
        pack.setPrice(new BigDecimal("9.90"));
        pack.setSort(0);
        pack.setStatus(ActiveStatus.ACTIVE);

        CreditPackage saved = directory.savePackage(pack);

        assertNotNull(saved.getId());
        assertNotNull(saved.getUpdatedAt());
        verify(packages).insert(saved);
    }

    @Test
    void updateWritesUpdatedAtBeforeTheRowIsStored() {
        CreditPackageMapper packages = mock(CreditPackageMapper.class);
        when(packages.updateById(any(CreditPackage.class))).thenAnswer(invocation -> {
            CreditPackage row = invocation.getArgument(0);
            assertNotNull(row.getUpdatedAt());
            return 1;
        });
        AdminDirectory directory = directory(packages);
        CreditPackage pack = new CreditPackage();
        pack.setId("01PACK00000000000000000000");
        pack.setName("基本版");

        directory.savePackage(pack);

        verify(packages).updateById(pack);
    }

    private static AdminDirectory directory(CreditPackageMapper packages) {
        return new AdminDirectory(null, null, null, packages, null, null, null, null, null, null);
    }
}
