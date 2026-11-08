package com.omino.shopai.app.dto;

import java.time.OffsetDateTime;

/**
 * 当前用户资料。手机号已脱敏，风格和字号不在这里。
 *
 * @param id 用户主键
 * @param nickname 昵称
 * @param avatarUrl 头像，可空
 * @param phone 脱敏手机号，可空
 * @param inviteCode 邀请码
 * @param locale 语言
 * @param pushEnabled 推送是否打开
 * @param createdAt 注册时间
 */
public record MeViewDto(
        String id,
        String nickname,
        String avatarUrl,
        String phone,
        String inviteCode,
        String locale,
        boolean pushEnabled,
        OffsetDateTime createdAt) {
}
