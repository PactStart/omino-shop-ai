package com.omino.shopai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omino.shopai.entity.AdminAuditLog;
import org.apache.ibatis.annotations.Insert;

public interface AdminAuditLogMapper extends BaseMapper<AdminAuditLog> {

    /**
     * 写入一条审计。detail 按 jsonb 绑定，空值保持空。
     *
     * @param row 审计行，detail 必须是合法 JSON 文本或空
     * @return 插入行数
     */
    @Insert("""
            INSERT INTO admin_audit_log (id, sso_sub, action, resource, detail, created_at)
            VALUES (#{id}, #{ssoSub}, #{action}, #{resource}, CAST(#{detail} AS jsonb), #{createdAt})
            """)
    int insertJson(AdminAuditLog row);
}
