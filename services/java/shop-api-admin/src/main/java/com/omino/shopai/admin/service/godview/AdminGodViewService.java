package com.omino.shopai.admin.service.godview;

import com.omino.shopai.admin.security.GodViewIssuer;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.AdminAuditLog;
import com.omino.shopai.mapper.AdminAuditLogMapper;
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
    private final AdminAuditLogMapper audits;
    private final boolean enabled;

    /**
     * 用环境里的密钥和开关构造签发器。
     *
     * @param secret 访问令牌密钥
     * @param audits 审计表
     * @param enabled 是否允许签发
     */
    @Autowired
    public AdminGodViewService(
            @Value("${JWT_ACCESS_SECRET:0123456789abcdef0123456789abcdef}") String secret,
            AdminAuditLogMapper audits,
            @Value("${GOD_VIEW_ENABLED:false}") boolean enabled) {
        this.issuer = new GodViewIssuer(secret);
        this.audits = audits;
        this.enabled = enabled;
    }
    /**
     * 注入签发器和审计表。关闭时签发直接拒绝。
     *
     * @param issuer 短时凭证签发
     * @param audits 审计表
     * @param enabled 是否允许签发
     */
    public AdminGodViewService(GodViewIssuer issuer, AdminAuditLogMapper audits, boolean enabled) {
        this.issuer = issuer;
        this.audits = audits;
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
        writeAudit(row);
        return token;
    }

    /**
     * 把签发动作写入审计表。
     *
     * @param row 审计行
     */
    protected void writeAudit(AdminAuditLog row) {
        audits.insertJson(row);
    }
}
