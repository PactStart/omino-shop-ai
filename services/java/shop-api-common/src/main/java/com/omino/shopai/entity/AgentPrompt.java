package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * AgentPrompt 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("agent_prompt")
public class AgentPrompt {

    @TableId(type = IdType.INPUT)
    private String key;
    private String name;
    private String content;
    private OffsetDateTime updatedAt;
    private String updatedBy;
}
