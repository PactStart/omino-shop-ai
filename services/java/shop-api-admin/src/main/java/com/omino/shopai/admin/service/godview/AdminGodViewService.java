package com.omino.shopai.admin.service.godview;

import com.omino.shopai.admin.security.GodViewIssuer;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.AdminAuditLog;
import com.omino.shopai.support.Ids;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
/**
 * AdminGodViewService 编排用例，事务和校验放在这里。
 */

@Service
public class AdminGodViewService {

    private final GodViewIssuer issuer;
    private final IAdminAudit audit;
    private final boolean enabled;

    @Autowired
    public AdminGodViewService(
            @Value("${JWT_ACCESS_SECRET:0123456789abcdef0123456789abcdef}") String secret,
            IAdminAudit audit,
            @Value("${GOD_VIEW_ENABLED:false}") boolean enabled) {
        this.issuer = new GodViewIssuer(secret);
        this.audit = audit;
        this.enabled = enabled;
    }
/**
 * 注入签发器和审计。关闭时由调用方拒绝。
 */

    public AdminGodViewService(GodViewIssuer issuer, IAdminAudit audit, boolean enabled) {
        this.issuer = issuer;
        this.audit = audit;
        this.enabled = enabled;
    }
/**
 * 签发短期凭证。
 */

    public String issue(String ssoSub, String userId) {
        if (!enabled) {
            throw new ApiBusinessException(ErrorCodes.GOD_VIEW_DISABLED, "上帝视角未开启");
        }
        if (userId == null || userId.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "userId 不能为空");
        }
        String token = issuer.issue(userId);
        AdminAuditLog row = new AdminAuditLog();
        row.setId(Ids.ulid());
        row.setSsoSub(ssoSub == null ? "unknown" : ssoSub);
        row.setAction("god_view.issue");
        row.setResource(userId);
        row.setDetail("{\"gv\":1}");
        row.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        audit.write(row);
        return token;
    }
}
