package com.omino.shopai.app.auth;

import com.omino.shopai.entity.AppInvite;
import com.omino.shopai.entity.AppUser;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 内存里的用户和邀请。成功的事务才看得见，失败丢掉这段改动。
 */
final class MemoryAccounts implements TransactionOperations {

    private final List<AppUser> committedUsers = new ArrayList<>();
    private final List<AppInvite> committedInvites = new ArrayList<>();
    private List<AppUser> pendingUsers;
    private List<AppInvite> pendingInvites;
    boolean failInviteInsert;

    /**
     * 在内存里开一段事务。成功才提交，失败丢掉这段改动。
     *
     * @param action 事务里的操作
     * @param <T> 操作的返回类型
     * @return 操作的返回值
     */
    @Override
    public <T> T execute(TransactionCallback<T> action) {
        pendingUsers = copyUsers(committedUsers);
        pendingInvites = copyInvites(committedInvites);
        try {
            TransactionStatus status = new SimpleTransactionStatus();
            T value = action.doInTransaction(status);
            committedUsers.clear();
            committedUsers.addAll(pendingUsers);
            committedInvites.clear();
            committedInvites.addAll(pendingInvites);
            return value;
        } catch (RuntimeException ex) {
            throw ex;
        } finally {
            pendingUsers = null;
            pendingInvites = null;
        }
    }

    /**
     * 按手机号读取未删除的用户。
     *
     * @param phone 手机号
     * @return 用户；没有则空
     */
    Optional<AppUser> findActiveByPhone(String phone) {
        return users().stream().filter(user -> phone.equals(user.getPhone()) && user.getDeletedAt() == null).findFirst();
    }

    /**
     * 按邀请码读取未删除的用户。
     *
     * @param inviteCode 邀请码
     * @return 用户；没有则空
     */
    Optional<AppUser> findActiveByInviteCode(String inviteCode) {
        return users().stream()
                .filter(user -> inviteCode.equals(user.getInviteCode()) && user.getDeletedAt() == null)
                .findFirst();
    }

    /**
     * 按主键读取用户的副本。
     *
     * @param userId 用户主键
     * @return 用户副本；没有则空
     */
    Optional<AppUser> findById(String userId) {
        return users().stream().filter(user -> userId.equals(user.getId())).findFirst().map(MemoryAccounts::copy);
    }

    /**
     * 放入用户的副本。
     *
     * @param user 新用户
     * @return 无
     */
    void insert(AppUser user) {
        users().add(copy(user));
    }

    /**
     * 只改邀请人和更新时间。
     *
     * @param userId 被邀请人
     * @param inviterId 邀请人
     * @param updatedAt 更新时间
     * @return 无
     */
    void updateInviter(String userId, String inviterId, OffsetDateTime updatedAt) {
        users().stream().filter(user -> userId.equals(user.getId())).findFirst().ifPresent(user -> {
            user.setInviterId(inviterId);
            user.setUpdatedAt(updatedAt);
        });
    }

    /**
     * 内存里不落身份、钱包和积分行。
     *
     * @param userId 用户主键
     * @param phone 手机号
     * @param now 写入时间
     * @return 无
     */
    void ensureAccounts(String userId, String phone, OffsetDateTime now) {
    }

    /**
     * 放入邀请关系的副本。打开失败开关时直接抛出。
     *
     * @param invite 邀请关系
     * @return 无
     */
    void insert(AppInvite invite) {
        if (failInviteInsert) {
            throw new IllegalStateException("invite insert failed");
        }
        invites().add(copy(invite));
    }

    /**
     * 数某个被邀请人的关系行。
     *
     * @param inviteeId 被邀请人
     * @return 行数
     */
    int countInvites(String inviteeId) {
        return (int) invites().stream().filter(invite -> inviteeId.equals(invite.getInviteeId())).count();
    }

    /**
     * 按被邀请人读取邀请关系。
     *
     * @param inviteeId 被邀请人
     * @return 邀请关系；没有则空
     */
    Optional<AppInvite> findByInvitee(String inviteeId) {
        return invites().stream().filter(invite -> inviteeId.equals(invite.getInviteeId())).findFirst();
    }

    /**
     * 事务进行中读未提交的用户，否则读已提交的用户。
     *
     * @return 当前可见的用户
     */
    private List<AppUser> users() {
        return pendingUsers != null ? pendingUsers : committedUsers;
    }

    /**
     * 事务进行中读未提交的邀请，否则读已提交的邀请。
     *
     * @return 当前可见的邀请
     */
    private List<AppInvite> invites() {
        return pendingInvites != null ? pendingInvites : committedInvites;
    }

    /**
     * 复制用户列表，避免未提交的改动碰到已提交的行。
     *
     * @param source 已提交的用户
     * @return 副本
     */
    private static List<AppUser> copyUsers(List<AppUser> source) {
        List<AppUser> copy = new ArrayList<>();
        for (AppUser user : source) {
            copy.add(copy(user));
        }
        return copy;
    }

    /**
     * 复制邀请列表，避免未提交的改动碰到已提交的行。
     *
     * @param source 已提交的邀请
     * @return 副本
     */
    private static List<AppInvite> copyInvites(List<AppInvite> source) {
        List<AppInvite> copy = new ArrayList<>();
        for (AppInvite invite : source) {
            copy.add(copy(invite));
        }
        return copy;
    }

    /**
     * 复制用户字段。
     *
     * @param source 原用户
     * @return 副本
     */
    private static AppUser copy(AppUser source) {
        AppUser copy = new AppUser();
        copy.setId(source.getId());
        copy.setNickname(source.getNickname());
        copy.setPhone(source.getPhone());
        copy.setStatus(source.getStatus());
        copy.setLocale(source.getLocale());
        copy.setInviteCode(source.getInviteCode());
        copy.setInviterId(source.getInviterId());
        copy.setPushEnabled(source.getPushEnabled());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setDeletedAt(source.getDeletedAt());
        return copy;
    }

    /**
     * 复制邀请字段。
     *
     * @param source 原邀请
     * @return 副本
     */
    private static AppInvite copy(AppInvite source) {
        AppInvite copy = new AppInvite();
        copy.setId(source.getId());
        copy.setInviterId(source.getInviterId());
        copy.setInviteeId(source.getInviteeId());
        copy.setStatus(source.getStatus());
        copy.setValidAt(source.getValidAt());
        copy.setCreatedAt(source.getCreatedAt());
        return copy;
    }
}
