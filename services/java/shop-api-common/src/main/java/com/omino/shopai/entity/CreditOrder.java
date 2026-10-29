package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.PayChannel;
import com.omino.shopai.enums.CreditOrderStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * CreditOrder 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("credit_order")
public class CreditOrder {

    @TableId(type = IdType.INPUT)
    private String id;
    private String userId;
    private String packageId;
    private BigDecimal credits;
    private BigDecimal payAmount;
    private PayChannel channel;
    private CreditOrderStatus status;
    private String clientOrderId;
    private String payTxnId;
    private OffsetDateTime paidAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
