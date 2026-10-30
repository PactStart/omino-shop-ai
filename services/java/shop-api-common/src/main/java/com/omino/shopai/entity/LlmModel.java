package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.OnOff;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * LlmModel 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("llm_model")
public class LlmModel {

    @TableId(type = IdType.INPUT)
    private String id;
    private String name;
    private String baseUrl;
    private String modelName;
    private byte[] apiKeyEnc;
    private Integer weight;
    private OnOff enabled;
    private Integer timeoutMs;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
