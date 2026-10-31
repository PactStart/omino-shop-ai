package com.omino.shopai.admin.service.godview;

import com.omino.shopai.entity.AdminAuditLog;
import com.omino.shopai.mapper.AdminAuditLogMapper;
import org.springframework.stereotype.Repository;
/**
 * 把上帝视角签发记进审计表。
 */

@Repository
public class AdminAuditImpl implements IAdminAudit {

    private final AdminAuditLogMapper logs;

    public AdminAuditImpl(AdminAuditLogMapper logs) {
        this.logs = logs;
    }

    @Override
    public void write(AdminAuditLog row) {
        logs.insert(row);
    }
}
