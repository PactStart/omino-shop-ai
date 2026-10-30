package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.ActiveStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * ChatSuggestion 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("chat_suggestion")
public class ChatSuggestion {

    @TableId(type = IdType.INPUT)
    private String id;
    private String text;
    private Integer sort;
    private ActiveStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
