package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * ConversationMessage 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("conversation_message")
public class ConversationMessage {

    @TableId(type = IdType.INPUT)
    private String id;
    private String conversationId;
    private String clientMsgId;
    private String role;
    private String content;
    private String intent;
    private String payload;
    private OffsetDateTime createdAt;
}
