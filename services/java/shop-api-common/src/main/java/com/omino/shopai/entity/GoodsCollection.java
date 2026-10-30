package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * GoodsCollection 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("goods_collection")
public class GoodsCollection {

    @TableId(type = IdType.INPUT)
    private String id;
    private String userId;
    private String conversationId;
    private String runId;
    private String title;
    private String source;
    private OffsetDateTime createdAt;
}
