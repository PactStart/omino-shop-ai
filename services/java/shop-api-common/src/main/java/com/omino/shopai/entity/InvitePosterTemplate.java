package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.ActiveStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * InvitePosterTemplate 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("invite_poster_template")
public class InvitePosterTemplate {

    @TableId(type = IdType.INPUT)
    private String id;
    private String name;
    private String imageObjectKey;
    private Integer canvasWidth;
    private Integer canvasHeight;
    private Integer qrX;
    private Integer qrY;
    private Integer qrWidth;
    private Integer qrHeight;
    private Integer sort;
    private ActiveStatus status;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
