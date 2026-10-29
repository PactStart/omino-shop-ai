package com.omino.shopai.app.service.invite;

import com.omino.shopai.entity.AppUser;
import com.omino.shopai.entity.InvitePosterTemplate;

import java.util.List;
import java.util.Optional;
/**
 * 邀请码和海报模板的读取端口。
 */

public interface InviteCatalog {

    List<InvitePosterTemplate> posters();

    Optional<AppUser> findById(String id);

    Optional<AppUser> findByInviteCode(String code);
}
