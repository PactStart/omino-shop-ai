package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.Platform;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * UserEvent 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("user_event")
public class UserEvent {

    @TableId(type = IdType.INPUT)
    private String id;
    private String userId;
    private String kind;
    private Platform platform;
    private String goodsId;
    private String platformGoodsId;
    private String orderId;
    private String title;
    private String payload;
    private OffsetDateTime createdAt;
}
