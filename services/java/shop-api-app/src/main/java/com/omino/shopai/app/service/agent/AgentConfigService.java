package com.omino.shopai.app.service.agent;

import com.omino.shopai.enums.ActiveStatus;
import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.ChatSuggestion;
import com.omino.shopai.support.Ids;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
/**
 * AgentConfigService 编排用例，事务和校验放在这里。
 */

@Service
public class AgentConfigService {

    private final AgentCatalog catalog;
    private final Clock clock;
/**
 * 注入提示词目录。生效条数上限在添加时检查。
 */

    public AgentConfigService(AgentCatalog catalog, Clock clock) {
        this.catalog = catalog;
        this.clock = clock;
    }
/**
 * 新增一条生效建议。超过上限则拒绝，避免对话入口被撑满。
 */

    public ChatSuggestion addActive(String text) {
        if (text == null || text.isBlank() || text.trim().length() > 128) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "推荐问题不正确");
        }
        if (catalog.activeSuggestionCount() >= 12) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "生效推荐不能超过 12 条");
        }
        ChatSuggestion row = new ChatSuggestion();
        row.setId(Ids.ulid());
        row.setText(text.trim());
        row.setSort(catalog.activeSuggestionCount());
        row.setStatus(ActiveStatus.ACTIVE);
        row.setCreatedAt(OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        row.setUpdatedAt(row.getCreatedAt());
        catalog.addSuggestion(row);
        return row;
    }
}
