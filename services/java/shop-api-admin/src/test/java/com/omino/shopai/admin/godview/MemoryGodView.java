package com.omino.shopai.admin.godview;

import com.omino.shopai.admin.security.GodViewIssuer;
import com.omino.shopai.admin.service.godview.AdminGodViewService;
import com.omino.shopai.entity.AdminAuditLog;

import java.util.List;

/**
 * 签发测试把审计行收进内存列表，签发逻辑仍走真实实现。
 */
final class MemoryGodView extends AdminGodViewService {

    private final List<AdminAuditLog> rows;

    /**
     * 绑定内存审计列表。
     *
     * @param issuer 短时凭证签发
     * @param rows 审计行
     * @param enabled 是否允许签发
     */
    MemoryGodView(GodViewIssuer issuer, List<AdminAuditLog> rows, boolean enabled) {
        super(issuer, null, enabled);
        this.rows = rows;
    }

    /**
     * 把审计行放进测试列表，不写数据库。
     *
     * @param row 审计行
     */
    @Override
    protected void writeAudit(AdminAuditLog row) {
        rows.add(row);
    }
}
