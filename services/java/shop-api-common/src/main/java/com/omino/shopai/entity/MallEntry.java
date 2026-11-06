package com.omino.shopai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omino.shopai.enums.MallActionType;
import com.omino.shopai.enums.MallZoneCode;
import com.omino.shopai.enums.PublishStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * MallEntry 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("mall_entry")
public class MallEntry {

    @TableId(type = IdType.INPUT)
    private String id;
    private MallZoneCode zone;
    private String categoryId;
    private String title;
    private String imageObjectKey;
    private Integer sort;
    private PublishStatus status;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    private String clientTypes;
    private String channels;
    private MallActionType actionType;
    private String actionPayload;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
