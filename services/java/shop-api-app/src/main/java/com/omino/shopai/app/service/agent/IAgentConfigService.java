package com.omino.shopai.app.service.agent;

import com.omino.shopai.app.dto.SuggestionViewDto;
import com.omino.shopai.entity.ChatSuggestion;

import java.util.List;

/**
 * 维护对话入口的生效推荐。
 */
public interface IAgentConfigService {

    /**
     * 新增一条生效建议。超过上限则拒绝，避免对话入口被撑满。
     *
     * @param text 推荐问题
     * @return 已写入的建议
     */
    ChatSuggestion addActive(String text);

    /**
     * 列出空对话要展示的推荐。只含生效行，按排序和主键升序。
     *
     * @return 推荐问题，没有则空列表
     */
    List<SuggestionViewDto> active();
}
