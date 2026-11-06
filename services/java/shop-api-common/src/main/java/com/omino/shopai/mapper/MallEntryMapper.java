package com.omino.shopai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omino.shopai.entity.MallEntry;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;

public interface MallEntryMapper extends BaseMapper<MallEntry> {

    /**
     * 插入一条商城条目。端、渠道和点击参数按 jsonb 绑定。
     *
     * @param row 已填好主键和时间的条目
     * @return 写入行数
     */
    @Insert("""
            INSERT INTO mall_entry
                (id, zone, category_id, title, image_object_key, sort, status, starts_at, ends_at,
                 client_types, channels, action_type, action_payload, created_at, updated_at)
            VALUES
                (#{id}, #{zone}, #{categoryId}, #{title}, #{imageObjectKey}, #{sort}, #{status}, #{startsAt}, #{endsAt},
                 CAST(#{clientTypes} AS jsonb), CAST(#{channels} AS jsonb), #{actionType},
                 CAST(#{actionPayload} AS jsonb), #{createdAt}, #{updatedAt})
            """)
    int insertJson(MallEntry row);

    /**
     * 按主键更新一条商城条目。jsonb 列仍按 jsonb 绑定。
     *
     * @param row 要更新的条目
     * @return 更新行数
     */
    @Update("""
            UPDATE mall_entry
            SET zone = #{zone},
                category_id = #{categoryId},
                title = #{title},
                image_object_key = #{imageObjectKey},
                sort = #{sort},
                status = #{status},
                starts_at = #{startsAt},
                ends_at = #{endsAt},
                client_types = CAST(#{clientTypes} AS jsonb),
                channels = CAST(#{channels} AS jsonb),
                action_type = #{actionType},
                action_payload = CAST(#{actionPayload} AS jsonb),
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int updateJson(MallEntry row);
}
