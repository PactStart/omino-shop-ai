package com.omino.shopai.admin.service.ops;

import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.ChatSuggestion;
import com.omino.shopai.enums.ActiveStatus;
import com.omino.shopai.exception.ApiBusinessException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 推荐问题的读取和上下架。不物理删除。同时上架最多 12 条。
 */
public final class SuggestionRules {

    private SuggestionRules() {
    }

    /**
     * 原样返回全部推荐，包含已下架。
     *
     * @param rows 库里的全部行
     * @return 同一批行，含 retired
     */
    public static List<ChatSuggestion> list(List<ChatSuggestion> rows) {
        return List.copyOf(rows);
    }

    /**
     * 修改文案、排序或上下架。第 13 条上架被拒绝时，这一行保持改之前的状态。
     *
     * @param rows 当前全部推荐
     * @param id 要改的主键
     * @param text 新文案；空表示不改
     * @param sort 新排序；空表示不改
     * @param status 新状态码；空表示不改
     * @return 改过的那一行
     */
    public static ChatSuggestion patch(List<ChatSuggestion> rows, String id, String text, Integer sort, String status) {
        ChatSuggestion row = rows.stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ApiBusinessException(ErrorCodes.NOT_FOUND, "推荐问题不存在"));
        String nextText = text == null ? row.getText() : text.trim();
        if (nextText == null || nextText.isEmpty() || nextText.length() > 128) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "文案长度须在 1 到 128 之间");
        }
        ActiveStatus next = status == null || status.isBlank() ? row.getStatus() : ActiveStatus.fromCode(status.trim());
        if (next == null) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "状态不正确");
        }
        // 先数已上架条数。超限就抛出，这一行的状态还没被改掉。
        if (next == ActiveStatus.ACTIVE && row.getStatus() != ActiveStatus.ACTIVE) {
            long active = rows.stream().filter(item -> item.getStatus() == ActiveStatus.ACTIVE).count();
            if (active >= 12) {
                throw new ApiBusinessException(ErrorCodes.VALIDATION, "生效推荐不能超过 12 条");
            }
        }
        row.setText(nextText);
        if (sort != null) {
            row.setSort(sort);
        }
        row.setStatus(next);
        row.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return row;
    }
}
