package com.omino.shopai.app.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.entity.AppInvite;
import com.omino.shopai.entity.AppUser;
import com.omino.shopai.mapper.AppInviteMapper;
import com.omino.shopai.mapper.AppUserMapper;
import com.omino.shopai.support.Ids;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
/**
 * 持久化边界。事务和状态判断由调用方负责。
 */

@Repository
public class MybatisUserAccountStore implements UserAccountStore, InviteStore {

    private final AppUserMapper users;
    private final AppInviteMapper invites;
    private final JdbcTemplate jdbc;
/**
 * 用户、钱包和积分账户的 MyBatis 访问。
 */

    public MybatisUserAccountStore(AppUserMapper users, AppInviteMapper invites, JdbcTemplate jdbc) {
        this.users = users;
        this.invites = invites;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AppUser> findActiveByPhone(String phone) {
        return Optional.ofNullable(users.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getPhone, phone)
                .isNull(AppUser::getDeletedAt)));
    }

    @Override
    public Optional<AppUser> findActiveByInviteCode(String inviteCode) {
        return Optional.ofNullable(users.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getInviteCode, inviteCode)
                .isNull(AppUser::getDeletedAt)));
    }

    @Override
    public Optional<AppUser> findById(String userId) {
        return Optional.ofNullable(users.selectById(userId));
    }

    @Override
    public void insert(AppUser user) {
        users.insert(user);
    }
/**
 * 更新已有记录。
 */

    @Override
    public void updateInviter(String userId, String inviterId, OffsetDateTime updatedAt) {
        AppUser patch = new AppUser();
        patch.setId(userId);
        patch.setInviterId(inviterId);
        patch.setUpdatedAt(updatedAt);
        users.updateById(patch);
    }
/**
 * 登录后保证钱包和积分账户存在，余额从零开始。
 */

    @Override
    public void ensureAccounts(String userId, String phone, OffsetDateTime now) {
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

    @Override
    public void insert(AppInvite invite) {
        invites.insert(invite);
    }

    @Override
    public Optional<AppInvite> findByInvitee(String inviteeId) {
        return Optional.ofNullable(invites.selectOne(new LambdaQueryWrapper<AppInvite>()
                .eq(AppInvite::getInviteeId, inviteeId)));
    }
}
