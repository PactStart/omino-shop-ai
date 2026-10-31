package com.omino.shopai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omino.shopai.entity.Announcement;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;

public interface AnnouncementMapper extends BaseMapper<Announcement> {

    @Insert("""
            INSERT INTO announcement
                (id, title, body, format, client_types, channels, version_code_min, version_code_max,
                 sort, status, starts_at, ends_at, created_at, updated_at)
            VALUES
                (#{id}, #{title}, #{body}, #{format}, CAST(#{clientTypes} AS jsonb), CAST(#{channels} AS jsonb),
                 #{versionCodeMin}, #{versionCodeMax}, #{sort}, #{status}, #{startsAt}, #{endsAt}, #{createdAt}, #{updatedAt})
            """)
    int insertJson(Announcement row);

    @Update("""
            UPDATE announcement
            SET title = #{title},
                body = #{body},
                format = #{format},
                client_types = CAST(#{clientTypes} AS jsonb),
                channels = CAST(#{channels} AS jsonb),
                version_code_min = #{versionCodeMin},
                version_code_max = #{versionCodeMax},
                sort = #{sort},
                status = #{status},
                starts_at = #{startsAt},
                ends_at = #{endsAt},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int updateJson(Announcement row);
}
