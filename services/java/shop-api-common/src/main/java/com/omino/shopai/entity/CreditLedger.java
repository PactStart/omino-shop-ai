package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.CreditLedgerType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * CreditLedger 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("credit_ledger")
public class CreditLedger {

    @TableId(type = IdType.INPUT)
    private String id;
    private String userId;
    private CreditLedgerType type;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String runId;
    private String creditOrderId;
    private Integer billedTokens;
    private String memo;
    private OffsetDateTime createdAt;
}
