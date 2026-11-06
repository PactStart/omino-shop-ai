package com.omino.shopai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omino.shopai.enums.PublishStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * MallCategory 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("mall_category")
public class MallCategory {

    @TableId(type = IdType.INPUT)
    private String id;
    private String name;
    private Integer sort;
    private PublishStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
