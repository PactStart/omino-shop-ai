package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * CreditAccount 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("credit_account")
public class CreditAccount {

    @TableId(type = IdType.INPUT)
    private String userId;
    private BigDecimal available;
    private OffsetDateTime updatedAt;
}
