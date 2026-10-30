package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.ActiveStatus;
import com.omino.shopai.enums.ClientType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * AppRuntimeConfig 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("app_runtime_config")
public class AppRuntimeConfig {

    @TableId(type = IdType.INPUT)
    private String id;
    private String name;
    private ClientType clientType;
    private String channel;
    private Integer versionCodeMin;
    private Integer versionCodeMax;
    private String payload;
    private Integer priority;
    private ActiveStatus status;
    private OffsetDateTime updatedAt;
}
