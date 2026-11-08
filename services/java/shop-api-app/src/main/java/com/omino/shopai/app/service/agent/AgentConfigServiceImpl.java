package com.omino.shopai.app.service.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.dto.SuggestionViewDto;
import com.omino.shopai.entity.ChatSuggestion;
import com.omino.shopai.enums.ActiveStatus;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.ChatSuggestionMapper;
import com.omino.shopai.support.Ids;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

/**
 * 添加生效推荐。条数上限在写入前检查。
 */
@Service
public class AgentConfigServiceImpl implements IAgentConfigService {

    private final ChatSuggestionMapper suggestions;
    private final Clock clock;

    /**
     * 注入推荐表。生效条数上限在添加时检查。
     *
     * @param suggestions 推荐问题
     * @param clock 创建时间
     */
    public AgentConfigServiceImpl(ChatSuggestionMapper suggestions, Clock clock) {
        this.suggestions = suggestions;
        this.clock = clock;
    }

    /**
     * 新增一条生效建议。超过上限则拒绝，避免对话入口被撑满。
     *
     * @param text 推荐问题
     * @return 已写入的建议
     */
    @Override
    public ChatSuggestion addActive(String text) {
        if (text == null || text.isBlank() || text.trim().length() > 128) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "推荐问题不正确");
        }
        if (activeSuggestionCount() >= 12) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "生效推荐不能超过 12 条");
        }
        ChatSuggestion row = new ChatSuggestion();
        row.setId(Ids.ulid());
        row.setText(text.trim());
        row.setSort(activeSuggestionCount());
        row.setStatus(ActiveStatus.ACTIVE);
        row.setCreatedAt(OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        row.setUpdatedAt(row.getCreatedAt());
        insertSuggestion(row);
        return row;
    }

    /**
     * 数出生效建议，添加前用来卡住上限。
     *
     * @return 生效条数
     */
    protected int activeSuggestionCount() {
        Long count = suggestions.selectCount(new LambdaQueryWrapper<ChatSuggestion>().eq(ChatSuggestion::getStatus, ActiveStatus.ACTIVE));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 列出空对话要展示的推荐。下架的不出现，没有配置则空列表。
     *
     * @return 推荐问题
     */
    @Override
    public List<SuggestionViewDto> active() {
        return loadAll().stream()
                .filter(row -> row.getStatus() == ActiveStatus.ACTIVE)
                .sorted(Comparator.comparing(ChatSuggestion::getSort, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ChatSuggestion::getId, Comparator.nullsLast(String::compareTo)))
                .map(row -> new SuggestionViewDto(row.getId(), row.getText()))
                .toList();
    }

    /**
     * 读出全部推荐，含已下架。筛选放在调用方。
     *
     * @return 全部推荐
     */
    protected List<ChatSuggestion> loadAll() {
        List<ChatSuggestion> rows = suggestions.selectList(null);
        return rows == null ? List.of() : rows;
    }

    /**
     * 写入一条推荐。
     *
     * @param suggestion 推荐问题
     */
    protected void insertSuggestion(ChatSuggestion suggestion) {
        suggestions.insert(suggestion);
    }
}
