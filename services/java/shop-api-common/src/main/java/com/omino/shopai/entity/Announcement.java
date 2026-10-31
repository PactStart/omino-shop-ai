package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.PublishStatus;
import com.omino.shopai.enums.RichTextFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * Announcement 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("announcement")
public class Announcement {

    @TableId(type = IdType.INPUT)
    private String id;
    private String title;
    private String body;
    private RichTextFormat format;
    private String clientTypes;
    private String channels;
    private Integer versionCodeMin;
    private Integer versionCodeMax;
    private Integer sort;
    private PublishStatus status;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
