package com.omino.shopai.app.service.chat;

import com.omino.shopai.entity.Conversation;

import java.util.Optional;
/**
 * 会话和消息的存储端口。
 */

public interface ConversationStore {

    Conversation create(String userId, String title);

    Optional<Conversation> findOwned(String conversationId, String userId);

    void insertUserMessage(String conversationId, String clientMsgId, String text, String runId);

    Optional<String> findRunId(String conversationId, String clientMsgId);
}
