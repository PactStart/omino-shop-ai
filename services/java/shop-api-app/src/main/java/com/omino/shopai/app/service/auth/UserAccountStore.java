package com.omino.shopai.app.service.auth;

import com.omino.shopai.entity.AppUser;

import java.time.OffsetDateTime;
import java.util.Optional;
/**
 * 用户和登录账户的存储端口。
 */

public interface UserAccountStore {

    Optional<AppUser> findActiveByPhone(String phone);

    Optional<AppUser> findActiveByInviteCode(String inviteCode);

    Optional<AppUser> findById(String userId);

    void insert(AppUser user);

    void updateInviter(String userId, String inviterId, OffsetDateTime updatedAt);

    void ensureAccounts(String userId, String phone, OffsetDateTime now);
}
