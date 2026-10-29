package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.Platform;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * ClickEvent 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("click_event")
public class ClickEvent {

    @TableId(type = IdType.INPUT)
    private String id;
    private String userId;
    private Platform platform;
    private String goodsId;
    private String platformGoodsId;
    private OffsetDateTime createdAt;
}
