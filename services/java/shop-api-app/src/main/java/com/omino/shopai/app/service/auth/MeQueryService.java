package com.omino.shopai.app.service.auth;

import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.AppUser;
import com.omino.shopai.mapper.AppUserMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
/**
 * MeQueryService 编排用例，事务和校验放在这里。
 */

@Service
public class MeQueryService {

    private final AppUserMapper users;

    public MeQueryService(AppUserMapper users) {
        this.users = users;
    }

    public Map<String, Object> get(String userId) {
        AppUser user = users.selectById(userId);
        if (user == null || user.getDeletedAt() != null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "用户不存在");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", user.getId());
        body.put("nickname", user.getNickname());
        body.put("avatarUrl", user.getAvatarUrl());
        body.put("phone", mask(user.getPhone()));
        body.put("inviteCode", user.getInviteCode());
        body.put("locale", user.getLocale());
        return body;
    }
/**
 * 脱敏后返回。
 */

    static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
