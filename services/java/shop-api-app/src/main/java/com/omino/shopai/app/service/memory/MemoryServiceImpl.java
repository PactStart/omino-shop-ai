package com.omino.shopai.app.service.memory;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.MemorySemantic;
import com.omino.shopai.enums.MemorySource;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.MemorySemanticMapper;
import com.omino.shopai.support.Ids;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 只读写当前用户的记忆。向量留空。
 */
@Service
public class MemoryServiceImpl implements IMemoryService {

    private static final BigDecimal CANDIDATE = new BigDecimal("0.8");
    private static final int MAX_LIMIT = 20;

    private final MemorySemanticMapper memories;

    /**
     * 注入记忆表。
     *
     * @param memories 记忆表
     */
    public MemoryServiceImpl(MemorySemanticMapper memories) {
        this.memories = memories;
    }

    /**
     * 读取还没过期的记忆，最近的在前。
     *
     * @param userId 当前用户
     * @param limit 条数，缺省 20，最大 20
     * @return 记忆列表，不含向量
     */
    @Override
    public List<MemorySemantic> search(String userId, Integer limit) {
        int size = limit == null ? MAX_LIMIT : limit;
        if (size < 1) {
            size = 1;
        }
        if (size > MAX_LIMIT) {
            size = MAX_LIMIT;
        }
        return memories.listActive(userId, size);
    }

    /**
     * 写入一条记忆。推断且把握低于 0.8 时只插入，不把旧行标成过期。
     *
     * @param userId 当前用户
     * @param kind 记忆种类
     * @param text 原文
     * @param confidence 把握，0 到 1
     * @param source explicit 或 inferred
     * @param supersedeId 要标成过期的旧记忆，可空
     * @return 新写入的行
     */
    @Override
    @Transactional
    public MemorySemantic write(String userId, String kind, String text, BigDecimal confidence, String source, String supersedeId) {
        if (kind == null || kind.isBlank() || kind.length() > 32 || text == null || text.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "记忆正文不能为空");
        }
        if (confidence == null || confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "把握必须在 0 到 1");
        }
        MemorySource parsed = parseSource(source);
        boolean candidate = parsed == MemorySource.INFERRED && confidence.compareTo(CANDIDATE) < 0;
        if (!candidate && supersedeId != null && !supersedeId.isBlank()) {
            // 先确认这行属于当前用户，再标过期，避免改到别人的记忆。
            MemorySemantic previous = memories.selectById(supersedeId);
            if (previous == null || !userId.equals(previous.getUserId())) {
                throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "记忆不存在");
            }
            memories.update(null, new LambdaUpdateWrapper<MemorySemantic>()
                    .eq(MemorySemantic::getId, supersedeId)
                    .eq(MemorySemantic::getUserId, userId)
                    .set(MemorySemantic::getValidTo, OffsetDateTime.now(ZoneOffset.UTC)));
        }
        MemorySemantic row = new MemorySemantic();
        row.setId(Ids.ulid());
        row.setUserId(userId);
        row.setKind(kind.trim());
        row.setText(text.trim());
        row.setConfidence(confidence);
        row.setSource(parsed);
        row.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        memories.insert(row);
        return row;
    }

    /**
     * 只接受明确记住和推断。问卷来源拒绝。
     *
     * @param source 请求里的来源
     * @return 可写入的来源
     */
    private static MemorySource parseSource(String source) {
        if ("explicit".equals(source)) {
            return MemorySource.EXPLICIT;
        }
        if ("inferred".equals(source)) {
            return MemorySource.INFERRED;
        }
        throw new ApiBusinessException(ErrorCodes.VALIDATION, "记忆来源无效");
    }
}
