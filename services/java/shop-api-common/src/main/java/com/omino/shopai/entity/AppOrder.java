package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.Platform;
import com.omino.shopai.enums.OrderStatus;
import com.omino.shopai.enums.SettleStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * AppOrder 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("app_order")
public class AppOrder {

    @TableId(type = IdType.INPUT)
    private String id;
    private String userId;
    private Platform platform;
    private String platformOrderId;
    private String relationId;
    private String goodsId;
    private String platformGoodsId;
    private String goodsTitle;
    private String goodsImageUrl;
    private BigDecimal payAmount;
    private String parentUserId;
    private BigDecimal mediaCommission;
    private BigDecimal userCommission;
    private String clickId;
    private OrderStatus orderStatus;
    private SettleStatus settleStatus;
    private String walletLedgerId;
    private OffsetDateTime orderedAt;
    private OffsetDateTime paidAt;
    private OffsetDateTime shippedAt;
    private OffsetDateTime completedAt;
    private String extras;
    private OffsetDateTime settledAt;
    private OffsetDateTime updatedFromPlatformAt;
    private OffsetDateTime createdAt;
}
