package com.omino.shopai.app.auth;

import com.omino.shopai.app.service.auth.AppJwtIssuer;
import com.omino.shopai.app.service.auth.SmsAuthServiceImpl;
import com.omino.shopai.entity.AppInvite;
import com.omino.shopai.entity.AppUser;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 用内存账户替换登录里的表访问。校验、建号和邀请绑定仍走真实实现。
 */
final class MemorySmsAuth extends SmsAuthServiceImpl {

    private final MemoryAccounts accounts;

    /**
     * 绑定同一份内存账户。表访问全部转给它。
     *
     * @param accounts 内存账户和事务
     * @param tokens 访问令牌
     * @param appEnv 运行环境
     * @param smsProvider 短信通道
     */
    MemorySmsAuth(MemoryAccounts accounts, AppJwtIssuer tokens, String appEnv, String smsProvider) {
        super(null, null, null, accounts, tokens, appEnv, smsProvider);
        this.accounts = accounts;
    }

    /**
     * 从内存账户按手机号读取未删除的用户。
     *
     * @param phone 手机号
     * @return 用户；没有则空
     */
    @Override
    protected Optional<AppUser> findActiveByPhone(String phone) {
        return accounts.findActiveByPhone(phone);
    }

    /**
     * 从内存账户按邀请码读取未删除的用户。
     *
     * @param inviteCode 邀请码
     * @return 用户；没有则空
     */
    @Override
    protected Optional<AppUser> findActiveByInviteCode(String inviteCode) {
        return accounts.findActiveByInviteCode(inviteCode);
    }

    /**
     * 把用户写入内存账户。
     *
     * @param user 新用户
     * @return 无
     */
    @Override
    protected void insertUser(AppUser user) {
        accounts.insert(user);
    }

    /**
     * 在内存账户上改邀请人和更新时间。
     *
     * @param userId 被邀请人
     * @param inviterId 邀请人
     * @param updatedAt 更新时间
     * @return 无
     */
    @Override
    protected void updateInviter(String userId, String inviterId, OffsetDateTime updatedAt) {
        accounts.updateInviter(userId, inviterId, updatedAt);
    }

    /**
     * 内存账户不落身份、钱包和积分行。
     *
     * @param userId 用户主键
     * @param phone 手机号
     * @param now 写入时间
     * @return 无
     */
    @Override
    protected void ensureAccounts(String userId, String phone, OffsetDateTime now) {
        accounts.ensureAccounts(userId, phone, now);
    }

    /**
     * 把邀请关系写入内存账户。
     *
     * @param invite 邀请关系
     * @return 无
     */
    @Override
    protected void insertInvite(AppInvite invite) {
        accounts.insert(invite);
    }
}
