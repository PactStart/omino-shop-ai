package com.omino.shopai.app.service.auth;

import com.omino.shopai.entity.AppInvite;

import java.util.Optional;
/**
 * 邀请码占用检查。
 */

public interface IInvite {

    void insert(AppInvite invite);

    Optional<AppInvite> findByInvitee(String inviteeId);
}
