package com.omino.shopai.entity;

import lombok.Getter;
import lombok.Setter;
import com.omino.shopai.enums.OnOff;
import com.omino.shopai.enums.PublishStatus;
import com.omino.shopai.enums.ClientType;
import com.omino.shopai.enums.RichTextFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * AppRelease 的持久化实体。访问器由 Lombok 生成，不手写 getter/setter。
 */
@Getter
@Setter
@TableName("app_release")
public class AppRelease {

    @TableId(type = IdType.INPUT)
    private String id;
    private ClientType clientType;
    private String channel;
    private Integer versionCode;
    private String versionName;
    private OnOff forceUpdate;
    private String title;
    private String changelog;
    private RichTextFormat format;
    private String downloadUrl;
    private PublishStatus status;
    private OffsetDateTime publishedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
