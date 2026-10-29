package com.omino.shopai.app.service.auth;

import com.omino.shopai.enums.AuthStatus;
import com.omino.shopai.enums.InviteStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.AppInvite;
import com.omino.shopai.entity.AppUser;
import com.omino.shopai.entity.PlatformAuthorization;
import com.omino.shopai.mapper.AppInviteMapper;
import com.omino.shopai.mapper.AppUserMapper;
import com.omino.shopai.mapper.PlatformAuthorizationMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * InternalAccountService 编排用例，事务和校验放在这里。
 */

@Service
public class InternalAccountService {

    private static final Set<String> PLATFORMS = Set.of("taobao", "jd", "pdd", "vip");

    private final AppUserMapper users;
    private final PlatformAuthorizationMapper authorizations;
    private final AppInviteMapper invites;
/**
 * Agent 查询本人资料、订单和收益时的编排。
 */

    public InternalAccountService(AppUserMapper users, PlatformAuthorizationMapper authorizations, AppInviteMapper invites) {
        this.users = users;
        this.authorizations = authorizations;
        this.invites = invites;
    }

    public Map<String, Object> me(String userId) {
        AppUser user = users.selectById(userId);
        if (user == null || user.getDeletedAt() != null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "用户不存在");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nickname", user.getNickname());
        body.put("registeredAt", user.getCreatedAt() == null ? null : user.getCreatedAt().toString());
        body.put("preference", Map.of());
        return body;
    }

    public List<Map<String, Object>> authorizations(String userId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlatformAuthorization row : authorizations.selectList(new LambdaQueryWrapper<PlatformAuthorization>()
                .eq(PlatformAuthorization::getUserId, userId))) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("platform", row.getPlatform());
            view.put("status", row.getStatus());
            view.put("authorizedAt", row.getAuthorizedAt() == null ? null : row.getAuthorizedAt().toString());
            rows.add(view);
        }
        return rows;
    }

    public Map<String, String> startAuthorization(String platform) {
        requirePlatform(platform);
        return Map.of("platform", platform, "authUrl", "https://union.invalid/authorize?platform=" + platform);
    }

    public Map<String, String> revokeAuthorization(String userId, String platform) {
        requirePlatform(platform);
        PlatformAuthorization row = authorizations.selectOne(new LambdaQueryWrapper<PlatformAuthorization>()
                .eq(PlatformAuthorization::getUserId, userId)
                .eq(PlatformAuthorization::getPlatform, platform)
                .last("LIMIT 1"));
        if (row == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "授权不存在");
        }
        row.setStatus(AuthStatus.REVOKED);
        row.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC));
        authorizations.updateById(row);
        return Map.of("platform", platform, "status", "revoked");
    }

    public Map<String, Object> affiliate(String userId) {
        Long total = invites.selectCount(new LambdaQueryWrapper<AppInvite>().eq(AppInvite::getInviterId, userId));
        Long valid = invites.selectCount(new LambdaQueryWrapper<AppInvite>()
                .eq(AppInvite::getInviterId, userId)
                .eq(AppInvite::getStatus, InviteStatus.VALID));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inviteCount", total == null ? 0 : total);
        body.put("validUsers", valid == null ? 0 : valid);
        body.put("contribution", new BigDecimal("0.00").toPlainString());
        return body;
    }
/**
 * 只接受四个平台。其它字符串直接拒绝。
 */

    private static void requirePlatform(String platform) {
        if (!PLATFORMS.contains(platform)) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "平台不正确");
        }
    }
}
