package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.Platform;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * OrderSyncCursor 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("order_sync_cursor")
public class OrderSyncCursor {

    @TableId(type = IdType.INPUT)
    private Platform platform;
    private OffsetDateTime lastSyncCreateTime;
    private OffsetDateTime lastSyncUpdateTime;
    private String lastError;
    private OffsetDateTime updatedAt;
}
