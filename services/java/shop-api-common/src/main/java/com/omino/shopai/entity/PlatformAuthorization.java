package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.Platform;
import com.omino.shopai.enums.AuthStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * PlatformAuthorization 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("platform_authorization")
public class PlatformAuthorization {

    @TableId(type = IdType.INPUT)
    private String id;
    private String userId;
    private Platform platform;
    private AuthStatus status;
    private String relationId;
    private OffsetDateTime authorizedAt;
    private OffsetDateTime revokedAt;
}
