package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * GoodsFavorite 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("goods_favorite")
public class GoodsFavorite {

    @TableId(type = IdType.INPUT)
    private String id;
    private String userId;
    private String goodsId;
    private OffsetDateTime createdAt;
}
