package com.omino.shopai.app.service.auth;

import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.dto.MeViewDto;
import com.omino.shopai.entity.AppUser;
import com.omino.shopai.enums.OnOff;
import com.omino.shopai.enums.UserStatus;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.AppUserMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 读取和修改当前用户自己的资料。
 */
@Service
public class MeQueryService {

    private final AppUserMapper users;

    /**
     * 注入用户表。
     *
     * @param users 用户表
     */
    public MeQueryService(AppUserMapper users) {
        this.users = users;
    }

    /**
     * 读取未注销用户的资料。手机号只留前三后四。
     *
     * @param userId 当前用户
     * @return 资料
     */
    public MeViewDto get(String userId) {
        AppUser user = requireActive(userId);
        return new MeViewDto(
                user.getId(),
                user.getNickname(),
                user.getAvatarUrl(),
                mask(user.getPhone()),
                user.getInviteCode(),
                user.getLocale(),
                user.getPushEnabled() != OnOff.OFF,
                user.getCreatedAt());
    }

    /**
     * 改推送总开关。
     *
     * @param userId 当前用户
     * @param enabled 打开为真
     */
    public void updatePush(String userId, Boolean enabled) {
        if (enabled == null) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "enabled 不能为空", "enabled", "NOT_NULL");
        }
        AppUser user = requireActive(userId);
        user.setPushEnabled(enabled ? OnOff.ON : OnOff.OFF);
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        save(user);
    }

    /**
     * 注销当前账号。上帝视角不能注销正在查看的人。确认词不对则不动数据。
     *
     * @param userId 当前用户
     * @param confirm 必须是 DELETE
     * @param godView 这张令牌是否带 gv=1
     */
    public void delete(String userId, String confirm, boolean godView) {
        if (godView) {
            throw new ApiBusinessException(ErrorCodes.FORBIDDEN, "上帝视角不能注销");
        }
        if (!"DELETE".equals(confirm)) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "confirm 必须是 DELETE", "confirm", "NOT_EQUALS");
        }
        AppUser user = requireActive(userId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // 软删后登录不再认这个手机号上的旧账号，安全链也不再放行这张 access token。
        user.setStatus(UserStatus.DISABLED);
        user.setDeletedAt(now);
        user.setUpdatedAt(now);
        save(user);
    }

    /**
     * 读取未删除的用户。找不到就当资源不存在。
     *
     * @param userId 用户主键
     * @return 用户
     */
    protected AppUser requireActive(String userId) {
        AppUser user = load(userId);
        if (user == null || user.getDeletedAt() != null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    /**
     * 按主键读用户。
     *
     * @param userId 用户主键
     * @return 用户；没有则空
     */
    protected AppUser load(String userId) {
        return users.selectById(userId);
    }

    /**
     * 写回用户。
     *
     * @param user 已改过的用户
     */
    protected void save(AppUser user) {
        users.updateById(user);
    }

    /**
     * 手机号留前三后四，太短则原样返回。
     *
     * @param phone 手机号，可空
     * @return 脱敏结果
     */
    static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
