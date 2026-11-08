package com.omino.shopai.app.service.chat;

import com.omino.shopai.app.dto.ConversationViewDto;
import com.omino.shopai.app.dto.TurnAcceptedDto;
import com.omino.shopai.dto.PageDto;

/**
 * 受理一轮对话并立刻返回 run。
 */
public interface IChatTurnService {

    /**
     * 校验文本后按 clientMsgId 幂等。已受理则返回原来的 runId。
     *
     * @param userId 用户主键
     * @param conversationId 会话主键，空则新建
     * @param clientMsgId 客户端消息号
     * @param text 用户文本
     * @return 已受理的 run
     */
    TurnAcceptedDto open(String userId, String conversationId, String clientMsgId, String text);

    /**
     * 按输入类型校验后受理。链接和图片可以没有正文。恢复动作也可以没有正文。
     *
     * @param userId 用户主键
     * @param command 这一轮的输入
     * @return 已受理的 run
     */
    TurnAcceptedDto open(String userId, TurnCommand command);

    /**
     * 按更新时间倒序列出当前用户的会话。不含消息。
     *
     * @param userId 用户主键
     * @param page 页码，从 1 起
     * @param size 页大小，最大 20
     * @return 会话页
     */
    PageDto<ConversationViewDto> list(String userId, int page, int size);
}
