package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.ActiveStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * CreditPackage 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("credit_package")
public class CreditPackage {

    @TableId(type = IdType.INPUT)
    private String id;
    private String name;
    private BigDecimal credits;
    private BigDecimal price;
    private Integer sort;
    private ActiveStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
