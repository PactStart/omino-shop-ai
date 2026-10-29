package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.Platform;
import com.omino.shopai.enums.OnOff;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * AppUserInstalledApp 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("app_user_installed_app")
public class AppUserInstalledApp {

    @TableId(type = IdType.INPUT)
    private String id;
    private String userId;
    private Platform platform;
    private OnOff installed;
    private OffsetDateTime reportedAt;
}
