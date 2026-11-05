package com.omino.shopai.app.auth;

import com.omino.shopai.enums.InviteStatus;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.app.service.auth.AppJwtIssuer;
import com.omino.shopai.app.service.auth.ISmsAuthService;
import com.omino.shopai.entity.AppInvite;
import com.omino.shopai.entity.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InviteBindTest {

    private MemoryAccounts accounts;
    private ISmsAuthService service;

    @BeforeEach
    void setUp() {
        accounts = new MemoryAccounts();
        service = new MemorySmsAuth(
                accounts,
                new AppJwtIssuer("0123456789abcdef0123456789abcdef", 1800),
                "local", "mock");
    }

    @Test
    void validInviteWritesUserAndRelationTogether() {
        service.login("13800000011", "888888", null);
        AppUser inviter = accounts.findActiveByPhone("13800000011").orElseThrow();

        service.login("13800000012", "888888", inviter.getInviteCode());

        AppUser invitee = accounts.findActiveByPhone("13800000012").orElseThrow();
        assertEquals(inviter.getId(), invitee.getInviterId());
        AppInvite relation = accounts.findByInvitee(invitee.getId()).orElseThrow();
        assertEquals(InviteStatus.VALID, relation.getStatus());
        assertEquals(inviter.getId(), relation.getInviterId());
        assertNotNull(relation.getValidAt());
    }

    @Test
    void failedInviteInsertRollsBackTheNewUser() {
        service.login("13800000021", "888888", null);
        String code = accounts.findActiveByPhone("13800000021").orElseThrow().getInviteCode();
        accounts.failInviteInsert = true;

        assertThrows(IllegalStateException.class, () -> service.login("13800000022", "888888", code));

        assertTrue(accounts.findActiveByPhone("13800000022").isEmpty());
        assertTrue(accounts.findByInvitee("missing").isEmpty());
    }

    @Test
    void selfInviteIsRejected() {
        service.login("13800000031", "888888", null);
        AppUser user = accounts.findActiveByPhone("13800000031").orElseThrow();

        assertThrows(ApiBusinessException.class, () -> service.login("13800000031", "888888", user.getInviteCode()));

        assertNull(accounts.findActiveByPhone("13800000031").orElseThrow().getInviterId());
        assertTrue(accounts.findByInvitee(user.getId()).isEmpty());
    }

    @Test
    void existingInviterIsNotReplaced() {
        service.login("13800000041", "888888", null);
        service.login("13800000042", "888888", null);
        String firstCode = accounts.findActiveByPhone("13800000041").orElseThrow().getInviteCode();
        String secondCode = accounts.findActiveByPhone("13800000042").orElseThrow().getInviteCode();
        service.login("13800000043", "888888", firstCode);

        service.login("13800000043", "888888", secondCode);

        AppUser invitee = accounts.findActiveByPhone("13800000043").orElseThrow();
        assertEquals(accounts.findActiveByPhone("13800000041").orElseThrow().getId(), invitee.getInviterId());
        assertEquals(1, accounts.countInvites(invitee.getId()));
    }
}
