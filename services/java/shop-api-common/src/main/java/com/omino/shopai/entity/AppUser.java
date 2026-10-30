package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.UserStatus;
import com.omino.shopai.enums.OnOff;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * AppUser 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("app_user")
public class AppUser {

    @TableId(type = IdType.INPUT)
    private String id;
    private String nickname;
    private String avatarUrl;
    private String phone;
    private UserStatus status;
    private String locale;
    private String inviteCode;
    private String inviterId;
    private OnOff pushEnabled;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
}
