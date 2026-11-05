package com.omino.shopai.app.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.dto.LoginSessionDto;
import com.omino.shopai.entity.AppInvite;
import com.omino.shopai.entity.AppUser;
import com.omino.shopai.enums.InviteStatus;
import com.omino.shopai.enums.OnOff;
import com.omino.shopai.enums.UserStatus;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.AppInviteMapper;
import com.omino.shopai.mapper.AppUserMapper;
import com.omino.shopai.support.Ids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 手机号验证码登录。校验、建号和邀请绑定都在这一处完成。
 */
@Service
public class SmsAuthServiceImpl implements ISmsAuthService {

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final String MOCK_CODE = "888888";

    private final AppUserMapper users;
    private final AppInviteMapper invites;
    private final JdbcTemplate jdbc;
    private final TransactionOperations transactions;
    private final AppJwtIssuer tokens;
    private final String appEnv;
    private final String smsProvider;

    /**
     * 注入用户、邀请、账户补齐和登录依赖。
     *
     * @param users 用户表
     * @param invites 邀请关系表
     * @param jdbc 补齐身份、钱包和积分账户
     * @param transactions 建号和绑定共用的事务
     * @param tokens 访问令牌
     * @param appEnv 运行环境
     * @param smsProvider 短信通道
     */
    public SmsAuthServiceImpl(
            AppUserMapper users,
            AppInviteMapper invites,
            JdbcTemplate jdbc,
            TransactionOperations transactions,
            AppJwtIssuer tokens,
            @Value("${APP_ENV:local}") String appEnv,
            @Value("${SMS_PROVIDER:mock}") String smsProvider) {
        this.users = users;
        this.invites = invites;
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.tokens = tokens;
        this.appEnv = appEnv;
        this.smsProvider = smsProvider;
    }

    /**
     * 发验证码前校验手机号。mock 不打外部短信。
     *
     * @param phone 手机号
     * @return 无
     */
    @Override
    public void send(String phone) {
        rejectMockInProd();
        requirePhone(phone);
        if (!"mock".equals(smsProvider)) {
            throw new ApiBusinessException(ErrorCodes.INTERNAL, "短信通道未接入");
        }
    }

    /**
     * 校验验证码并完成登录。新用户在同一事务里建号，邀请码在这一次里绑定。
     *
     * @param phone 手机号
     * @param code 验证码
     * @param inviteCode 邀请码，可空
     * @return 登录会话
     */
    @Override
    public LoginSessionDto login(String phone, String code, String inviteCode) {
        rejectMockInProd();
        requirePhone(phone);
        if (!"mock".equals(smsProvider) || !MOCK_CODE.equals(code)) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "验证码错误");
        }
        // 建号、补齐账户和邀请绑定在同一事务里。失败则用户和邀请都不留下。
        return transactions.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            AppUser user = findActiveByPhone(phone).orElseGet(() -> createUser(phone, now));
            bindInvite(user, inviteCode, now);
            return new LoginSessionDto(tokens.issue(user.getId()), user.getId(), user.getInviteCode(), tokens.ttlSeconds());
        });
    }

    /**
     * 按手机号读取未删除的用户。
     *
     * @param phone 手机号
     * @return 用户；没有则空
     */
    protected Optional<AppUser> findActiveByPhone(String phone) {
        return Optional.ofNullable(users.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getPhone, phone)
                .isNull(AppUser::getDeletedAt)));
    }

    /**
     * 按邀请码读取未删除的用户。
     *
     * @param inviteCode 邀请码
     * @return 用户；没有则空
     */
    protected Optional<AppUser> findActiveByInviteCode(String inviteCode) {
        return Optional.ofNullable(users.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getInviteCode, inviteCode)
                .isNull(AppUser::getDeletedAt)));
    }

    /**
     * 插入用户。
     *
     * @param user 新用户
     * @return 无
     */
    protected void insertUser(AppUser user) {
        users.insert(user);
    }

    /**
     * 只改邀请人和更新时间。
     *
     * @param userId 被邀请人
     * @param inviterId 邀请人
     * @param updatedAt 更新时间
     * @return 无
     */
    protected void updateInviter(String userId, String inviterId, OffsetDateTime updatedAt) {
        AppUser patch = new AppUser();
        patch.setId(userId);
        patch.setInviterId(inviterId);
        patch.setUpdatedAt(updatedAt);
        users.updateById(patch);
    }

    /**
     * 登录后补齐手机号身份、空钱包和零积分账户。
     *
     * @param userId 用户主键
     * @param phone 手机号
     * @param now 写入时间
     * @return 无
     */
    protected void ensureAccounts(String userId, String phone, OffsetDateTime now) {
        jdbc.update("""
                INSERT INTO app_user_identity (id, user_id, provider, provider_uid, created_at)
                VALUES (?, ?, 'phone', ?, ?)
                """, Ids.ulid(), userId, phone, now);
        jdbc.update("""
                INSERT INTO wallet (user_id, available_amount, frozen_amount, total_earned, updated_at)
                VALUES (?, 0, 0, 0, ?)
                """, userId, now);
        jdbc.update("""
                INSERT INTO credit_account (user_id, available, updated_at)
                VALUES (?, 0, ?)
                """, userId, now);
    }

    /**
     * 插入邀请关系。
     *
     * @param invite 邀请关系
     * @return 无
     */
    protected void insertInvite(AppInvite invite) {
        invites.insert(invite);
    }

    /**
     * 写入新用户，并补齐身份、钱包和积分账户。
     *
     * @param phone 手机号
     * @param now 创建时间
     * @return 新用户
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
        insertUser(user);
        ensureAccounts(user.getId(), phone, now);
        return user;
    }

    /**
     * 绑定邀请关系。空码、找不到邀请人或已有上级时不改。自己邀请自己直接拒绝。
     *
     * @param user 当前登录用户
     * @param inviteCode 邀请码，可空
     * @param now 绑定时间
     * @return 无
     */
    private void bindInvite(AppUser user, String inviteCode, OffsetDateTime now) {
        if (inviteCode == null || inviteCode.isBlank() || user.getInviterId() != null) {
            return;
        }
        AppUser inviter = findActiveByInviteCode(inviteCode).orElse(null);
        if (inviter == null) {
            return;
        }
        if (inviter.getId().equals(user.getId())) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "不能邀请自己");
        }
        updateInviter(user.getId(), inviter.getId(), now);
        user.setInviterId(inviter.getId());
        AppInvite invite = new AppInvite();
        invite.setId(Ids.ulid());
        invite.setInviterId(inviter.getId());
        invite.setInviteeId(user.getId());
        invite.setStatus(InviteStatus.VALID);
        invite.setValidAt(now);
        invite.setCreatedAt(now);
        insertInvite(invite);
    }

    /**
     * 生成尚未占用的邀请码。碰撞则重试，不复用别人的码。
     *
     * @return 邀请码
     */
    private String freshInviteCode() {
        for (int i = 0; i < 8; i++) {
            String code = Ids.inviteCode();
            if (findActiveByInviteCode(code).isEmpty()) {
                return code;
            }
        }
        throw new ApiBusinessException(ErrorCodes.INTERNAL, "邀请码生成失败");
    }

    /**
     * 生产环境拒绝 mock 短信。
     *
     * @return 无
     */
    private void rejectMockInProd() {
        if ("prod".equals(appEnv) && "mock".equals(smsProvider)) {
            throw new IllegalStateException("prod 禁止 mock 短信");
        }
    }

    /**
     * 只接受大陆 11 位手机号，其余直接 40001。
     *
     * @param phone 手机号
     * @return 无
     */
    private static void requirePhone(String phone) {
        if (phone == null || !PHONE.matcher(phone).matches()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "手机号不正确");
        }
    }
}
