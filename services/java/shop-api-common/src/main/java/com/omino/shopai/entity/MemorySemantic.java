package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.MemorySource;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * MemorySemantic 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 * 不映射 embedding，避免把向量读进助手的检索结果。
 */
@Getter
@Setter
@TableName("memory_semantic")
public class MemorySemantic {

    @TableId(type = IdType.INPUT)
    private String id;
    private String userId;
    private String kind;
    private String text;
    private BigDecimal confidence;
    private MemorySource source;
    private OffsetDateTime validTo;
    private OffsetDateTime createdAt;
}
