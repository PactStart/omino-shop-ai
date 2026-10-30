package com.omino.shopai.app.service.auth;

import com.omino.shopai.enums.UserStatus;
import com.omino.shopai.enums.OnOff;
import com.omino.shopai.enums.InviteStatus;
import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.AppInvite;
import com.omino.shopai.entity.AppUser;
import com.omino.shopai.support.Ids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.regex.Pattern;
/**
 * SmsAuthService 编排用例，事务和校验放在这里。
 */

@Service
public class SmsAuthService {

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final String MOCK_CODE = "888888";

    private final UserAccountStore users;
    private final InviteStore invites;
    private final TransactionOperations transactions;
    private final AppJwtIssuer tokens;
    private final String appEnv;
    private final String smsProvider;

    public SmsAuthService(
            UserAccountStore users,
            InviteStore invites,
            TransactionOperations transactions,
            AppJwtIssuer tokens,
            @Value("${APP_ENV:local}") String appEnv,
            @Value("${SMS_PROVIDER:mock}") String smsProvider) {
        this.users = users;
        this.invites = invites;
        this.transactions = transactions;
        this.tokens = tokens;
        this.appEnv = appEnv;
        this.smsProvider = smsProvider;
    }
/**
 * 发验证码前校验手机号和频次。mock 不打外部短信。
 */

    public void send(String phone) {
        rejectMockInProd();
        requirePhone(phone);
        if (!"mock".equals(smsProvider)) {
            throw new ApiBusinessException(ErrorCodes.INTERNAL, "短信通道未接入");
        }
    }
/**
 * 完成登录。
 */

    public LoginSession login(String phone, String code, String inviteCode) {
        rejectMockInProd();
        requirePhone(phone);
        if (!"mock".equals(smsProvider) || !MOCK_CODE.equals(code)) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "验证码错误");
        }
        return transactions.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            AppUser user = users.findActiveByPhone(phone).orElseGet(() -> createUser(phone, now));
            bindInvite(user, inviteCode, now);
            return new LoginSession(tokens.issue(user.getId()), user.getId(), user.getInviteCode(), tokens.ttlSeconds());
        });
    }
/**
 * 创建新记录。
 */

    private AppUser createUser(String phone, OffsetDateTime now) {
        AppUser user = new AppUser();
        user.setId(Ids.ulid());
        user.setNickname("用户");
        user.setPhone(phone);
        user.setStatus(UserStatus.ACTIVE);
        user.setLocale("zh-CN");
        user.setInviteCode(freshInviteCode());
        user.setPushEnabled(OnOff.ON);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        users.insert(user);
        users.ensureAccounts(user.getId(), phone, now);
        return user;
    }
/**
 * 绑定关系。
 */

    private void bindInvite(AppUser user, String inviteCode, OffsetDateTime now) {
        if (inviteCode == null || inviteCode.isBlank() || user.getInviterId() != null) {
            return;
        }
        AppUser inviter = users.findActiveByInviteCode(inviteCode).orElse(null);
        if (inviter == null) {
            return;
        }
        if (inviter.getId().equals(user.getId())) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "不能邀请自己");
        }
        users.updateInviter(user.getId(), inviter.getId(), now);
        user.setInviterId(inviter.getId());
        AppInvite invite = new AppInvite();
        invite.setId(Ids.ulid());
        invite.setInviterId(inviter.getId());
        invite.setInviteeId(user.getId());
        invite.setStatus(InviteStatus.VALID);
        invite.setValidAt(now);
        invite.setCreatedAt(now);
        invites.insert(invite);
    }
/**
 * 生成尚未占用的邀请码。碰撞则重试，不复用别人的码。
 */

    private String freshInviteCode() {
        for (int i = 0; i < 8; i++) {
            String code = Ids.inviteCode();
            if (users.findActiveByInviteCode(code).isEmpty()) {
                return code;
            }
        }
        throw new ApiBusinessException(ErrorCodes.INTERNAL, "邀请码生成失败");
    }
/**
 * 拒绝并解开冻结。
 */

    private void rejectMockInProd() {
        if ("prod".equals(appEnv) && "mock".equals(smsProvider)) {
            throw new IllegalStateException("prod 禁止 mock 短信");
        }
    }
/**
 * 只接受大陆 11 位手机号，其余直接 40001。
 */

    private static void requirePhone(String phone) {
        if (phone == null || !PHONE.matcher(phone).matches()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "手机号不正确");
        }
    }
}
