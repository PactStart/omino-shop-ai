package com.omino.shopai.admin.service.godview;

import com.omino.shopai.entity.AdminAuditLog;
/**
 * 上帝视角签发的审计端口。动作名固定为 god_view.issue。
 */

public interface AdminAudit {

    void write(AdminAuditLog row);
}
