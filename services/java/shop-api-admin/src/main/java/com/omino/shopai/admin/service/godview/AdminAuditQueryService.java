package com.omino.shopai.admin.service.godview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.admin.support.AdminPages;
import com.omino.shopai.dto.PageDto;
import com.omino.shopai.entity.AdminAuditLog;
import com.omino.shopai.mapper.AdminAuditLogMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 审计只读。明细里不露出密钥、PID 和完整收款账号。
 */
@Service
public class AdminAuditQueryService {

    private final AdminAuditLogMapper logs;

    /**
     * 注入审计表。
     *
     * @param logs 审计
     */
    public AdminAuditQueryService(AdminAuditLogMapper logs) {
        this.logs = logs;
    }

    /**
     * 按时间倒序分页。
     *
     * @param page 页码
     * @param size 页大小
     * @return 审计页
     */
    public PageDto<Map<String, Object>> list(Integer page, Integer size) {
        List<Map<String, Object>> rows = logs.selectList(new LambdaQueryWrapper<AdminAuditLog>()
                        .orderByDesc(AdminAuditLog::getCreatedAt))
                .stream()
                .map(this::row)
                .toList();
        return AdminPages.of(rows, page, size);
    }

    private Map<String, Object> row(AdminAuditLog log) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", log.getId());
        row.put("ssoSub", log.getSsoSub());
        row.put("action", log.getAction());
        row.put("resource", log.getResource());
        row.put("detail", publicDetail(log.getDetail()));
        row.put("createdAt", log.getCreatedAt());
        return row;
    }

    /**
     * 丢掉可能含密钥、推广位或完整账号的明细。
     *
     * @param detail 库存明细
     * @return 可以给页面看的明细；不安全时为 null
     */
    static String publicDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return detail;
        }
        String lower = detail.toLowerCase(Locale.ROOT);
        if (lower.contains("secret") || lower.contains("apikey") || lower.contains("api_key")
                || lower.contains("password") || lower.contains("pid") || lower.contains("relation")) {
            return null;
        }
        if (detail.matches(".*\\d{8,}.*")) {
            return null;
        }
        return detail;
    }
}
