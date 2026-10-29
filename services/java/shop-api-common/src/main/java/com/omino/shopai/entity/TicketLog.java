package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.TicketLogKind;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * TicketLog 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("ticket_log")
public class TicketLog {

    @TableId(type = IdType.INPUT)
    private String id;
    private String ticketId;
    private TicketLogKind kind;
    private String actorType;
    private String actorUserId;
    private String actorSsoSub;
    private String fromValue;
    private String toValue;
    private String content;
    private String payload;
    private OffsetDateTime createdAt;
}
