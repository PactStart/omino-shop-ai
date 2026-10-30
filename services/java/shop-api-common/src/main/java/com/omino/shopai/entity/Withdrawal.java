package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.WithdrawalStatus;
import com.omino.shopai.enums.PayoutChannel;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Withdrawal 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("withdrawal")
public class Withdrawal {

    @TableId(type = IdType.INPUT)
    private String id;
    private String userId;
    private BigDecimal amount;
    private WithdrawalStatus status;
    private PayoutChannel channel;
    private String payeeAccount;
    private String accountMask;
    private String rejectReason;
    private String providerTransferId;
    private OffsetDateTime paidAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
