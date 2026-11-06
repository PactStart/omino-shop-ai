package com.omino.shopai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omino.shopai.enums.MallZoneCode;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * MallZone 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("mall_zone")
public class MallZone {

    @TableId(type = IdType.INPUT)
    private MallZoneCode code;
    private Integer columns;
    private Integer intervalMs;
    private OffsetDateTime updatedAt;
}
