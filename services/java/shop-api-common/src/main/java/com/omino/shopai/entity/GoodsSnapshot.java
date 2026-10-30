package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.Platform;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * GoodsSnapshot 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("goods_snapshot")
public class GoodsSnapshot {

    @TableId(type = IdType.INPUT)
    private String id;
    private Platform platform;
    private String platformGoodsId;
    private String platformGoodsAlias;
    private String title;
    private String imageUrl;
    private BigDecimal price;
    private BigDecimal couponPrice;
    private String shopName;
    private Long salesCount;
    private Integer reviewCount;
    private String goodsUrl;
    private String rawHash;
    private OffsetDateTime updatedAt;
}
