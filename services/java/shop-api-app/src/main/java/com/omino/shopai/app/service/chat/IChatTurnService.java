package com.omino.shopai.app.service.chat;

import com.omino.shopai.app.dto.TurnAcceptedDto;

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
}
