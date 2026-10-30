package com.omino.shopai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omino.shopai.entity.UserEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;

public interface UserEventMapper extends BaseMapper<UserEvent> {

    @Insert("""
            INSERT INTO user_event
                (id, user_id, kind, platform, goods_id, platform_goods_id, title, payload, created_at)
            VALUES
                (#{id}, #{userId}, #{kind}, #{platform}, #{goodsId}, #{platformGoodsId}, #{title},
                 CAST(#{payload} AS jsonb), #{createdAt})
            """)
    int insertEvent(
            @Param("id") String id,
            @Param("userId") String userId,
            @Param("kind") String kind,
            @Param("platform") String platform,
            @Param("goodsId") String goodsId,
            @Param("platformGoodsId") String platformGoodsId,
            @Param("title") String title,
            @Param("payload") String payload,
            @Param("createdAt") OffsetDateTime createdAt);
}
