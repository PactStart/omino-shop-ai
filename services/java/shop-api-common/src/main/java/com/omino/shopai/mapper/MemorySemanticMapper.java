package com.omino.shopai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omino.shopai.entity.MemorySemantic;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface MemorySemanticMapper extends BaseMapper<MemorySemantic> {

    @Select("""
            SELECT id, user_id AS userId, kind, text, confidence, source, valid_to AS validTo, created_at AS createdAt
            FROM memory_semantic
            WHERE user_id = #{userId} AND valid_to IS NULL
            ORDER BY created_at DESC
            LIMIT #{limit}
            """)
    List<MemorySemantic> listActive(@Param("userId") String userId, @Param("limit") int limit);
}
