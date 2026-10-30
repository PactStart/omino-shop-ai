package com.omino.shopai.app.auth;

import com.omino.shopai.app.service.auth.InviteStore;
import com.omino.shopai.app.service.auth.UserAccountStore;
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

final class MemoryAccounts implements UserAccountStore, InviteStore, TransactionOperations {

    private final List<AppUser> committedUsers = new ArrayList<>();
    private final List<AppInvite> committedInvites = new ArrayList<>();
    private List<AppUser> pendingUsers;
    private List<AppInvite> pendingInvites;
    boolean failInviteInsert;

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

    @Override
    public Optional<AppUser> findActiveByPhone(String phone) {
        return users().stream().filter(user -> phone.equals(user.getPhone()) && user.getDeletedAt() == null).findFirst();
    }

    @Override
    public Optional<AppUser> findActiveByInviteCode(String inviteCode) {
        return users().stream()
                .filter(user -> inviteCode.equals(user.getInviteCode()) && user.getDeletedAt() == null)
                .findFirst();
    }

    @Override
    public Optional<AppUser> findById(String userId) {
        return users().stream().filter(user -> userId.equals(user.getId())).findFirst().map(MemoryAccounts::copy);
    }

    @Override
    public void insert(AppUser user) {
        users().add(copy(user));
    }

    @Override
    public void updateInviter(String userId, String inviterId, OffsetDateTime updatedAt) {
        users().stream().filter(user -> userId.equals(user.getId())).findFirst().ifPresent(user -> {
            user.setInviterId(inviterId);
            user.setUpdatedAt(updatedAt);
        });
    }

    @Override
    public void ensureAccounts(String userId, String phone, OffsetDateTime now) {
    }

    @Override
    public void insert(AppInvite invite) {
        if (failInviteInsert) {
            throw new IllegalStateException("invite insert failed");
        }
        invites().add(copy(invite));
    }

    int countInvites(String inviteeId) {
        return (int) invites().stream().filter(invite -> inviteeId.equals(invite.getInviteeId())).count();
    }

    @Override
    public Optional<AppInvite> findByInvitee(String inviteeId) {
        return invites().stream().filter(invite -> inviteeId.equals(invite.getInviteeId())).findFirst();
    }

    private List<AppUser> users() {
        return pendingUsers != null ? pendingUsers : committedUsers;
    }

    private List<AppInvite> invites() {
        return pendingInvites != null ? pendingInvites : committedInvites;
    }

    private static List<AppUser> copyUsers(List<AppUser> source) {
        List<AppUser> copy = new ArrayList<>();
        for (AppUser user : source) {
            copy.add(copy(user));
        }
        return copy;
    }

    private static List<AppInvite> copyInvites(List<AppInvite> source) {
        List<AppInvite> copy = new ArrayList<>();
        for (AppInvite invite : source) {
            copy.add(copy(invite));
        }
        return copy;
    }

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
