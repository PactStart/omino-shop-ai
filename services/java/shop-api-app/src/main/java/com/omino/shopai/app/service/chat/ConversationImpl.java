package com.omino.shopai.app.service.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.entity.Conversation;
import com.omino.shopai.entity.ConversationMessage;
import com.omino.shopai.mapper.ConversationMapper;
import com.omino.shopai.mapper.ConversationMessageMapper;
import com.omino.shopai.support.Ids;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
/**
 * 持久化边界。事务和状态判断由调用方负责。
 */

@Repository
public class ConversationImpl implements IConversation {

    private final ConversationMapper conversations;
    private final ConversationMessageMapper messages;
    private final ObjectMapper objectMapper;
/**
 * 会话和消息的 MyBatis 访问。
 */

    public ConversationImpl(
            ConversationMapper conversations,
            ConversationMessageMapper messages,
            ObjectMapper objectMapper) {
        this.conversations = conversations;
        this.messages = messages;
        this.objectMapper = objectMapper;
    }
/**
 * 创建新记录。
 */

    @Override
    public Conversation create(String userId, String title) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Conversation conversation = new Conversation();
        conversation.setId(Ids.ulid());
        conversation.setUserId(userId);
        conversation.setTitle(title);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversations.insert(conversation);
        return conversation;
    }
/**
 * 按条件查找。
 */

    @Override
    public Optional<Conversation> findOwned(String conversationId, String userId) {
        Conversation conversation = conversations.selectById(conversationId);
        if (conversation == null || !userId.equals(conversation.getUserId())) {
            return Optional.empty();
        }
        return Optional.of(conversation);
    }
/**
 * 插入一行。
 */

    @Override
    public void insertUserMessage(String conversationId, String clientMsgId, String text, String runId) {
        ConversationMessage message = new ConversationMessage();
        message.setId(Ids.ulid());
        message.setConversationId(conversationId);
        message.setClientMsgId(clientMsgId);
        message.setRole("user");
        message.setContent(text);
        message.setPayload("{\"runId\":\"" + runId + "\"}");
        message.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        messages.insert(message);
    }
/**
 * 按条件查找。
 */

    @Override
    public Optional<String> findRunId(String conversationId, String clientMsgId) {
        ConversationMessage message = messages.selectOne(new LambdaQueryWrapper<ConversationMessage>()
                .eq(ConversationMessage::getConversationId, conversationId)
                .eq(ConversationMessage::getClientMsgId, clientMsgId));
        if (message == null || message.getPayload() == null) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            JsonNode runId = node.get("runId");
            return runId == null || runId.asText().isBlank() ? Optional.empty() : Optional.of(runId.asText());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
