package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.PublishStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * KbArticle 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("kb_article")
public class KbArticle {

    @TableId(type = IdType.INPUT)
    private String id;
    private String title;
    private String body;
    private PublishStatus status;
    private OffsetDateTime updatedAt;
}
