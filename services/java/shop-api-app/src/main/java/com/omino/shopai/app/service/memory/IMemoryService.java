package com.omino.shopai.app.service.memory;

import com.omino.shopai.entity.MemorySemantic;

import java.math.BigDecimal;
import java.util.List;

/**
 * 读写当前用户的语义记忆。助手不直接写表。
 */
public interface IMemoryService {

    /**
     * 读取还没过期的记忆，最近的在前。
     *
     * @param userId 当前用户
     * @param limit 条数，缺省 20，最大 20
     * @return 记忆列表，不含向量
     */
    List<MemorySemantic> search(String userId, Integer limit);

    /**
     * 写入一条记忆。候选记忆不覆盖已有行。
     *
     * @param userId 当前用户
     * @param kind 记忆种类，如 budget、platform
     * @param text 原文
     * @param confidence 把握，0 到 1
     * @param source explicit 或 inferred
     * @param supersedeId 要标成过期的旧记忆，可空
     * @return 新写入的行
     */
    MemorySemantic write(String userId, String kind, String text, BigDecimal confidence, String source, String supersedeId);
}
