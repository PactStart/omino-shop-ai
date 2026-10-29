package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.WalletLedgerType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * WalletLedger 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("wallet_ledger")
public class WalletLedger {

    @TableId(type = IdType.INPUT)
    private String id;
    private String userId;
    private WalletLedgerType type;
    private BigDecimal amount;
    private String orderId;
    private String withdrawalId;
    private String creditOrderId;
    private String memo;
    private OffsetDateTime createdAt;
}
