package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.InviteStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * AppInvite 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("app_invite")
public class AppInvite {

    @TableId(type = IdType.INPUT)
    private String id;
    private String inviterId;
    private String inviteeId;
    private InviteStatus status;
    private OffsetDateTime validAt;
    private OffsetDateTime createdAt;
}
