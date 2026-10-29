package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.TicketStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Ticket 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("ticket")
public class Ticket {

    @TableId(type = IdType.INPUT)
    private String id;
    private String userId;
    private String conversationId;
    private String category;
    private String content;
    private String orderId;
    private String priority;
    private TicketStatus status;
    private String assigneeSsoSub;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
