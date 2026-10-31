package com.omino.shopai.admin.service.ops;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.admin.support.AdminPages;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.dto.PageDto;
import com.omino.shopai.entity.Conversation;
import com.omino.shopai.entity.ConversationMessage;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.ConversationMapper;
import com.omino.shopai.mapper.ConversationMessageMapper;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话只读。不改用户说过的话。
 */
@Service
public class AdminConversationService {

    private final ConversationMapper conversations;
    private final ConversationMessageMapper messages;

    /**
     * 注入会话和消息。
     *
     * @param conversations 会话表
     * @param messages 消息表
     */
    public AdminConversationService(ConversationMapper conversations, ConversationMessageMapper messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    /**
     * 按更新时间倒序分页。可按用户收窄。
     *
     * @param userId 用户主键，可空
     * @param page 页码
     * @param size 页大小
     * @return 会话页
     */
    public PageDto<Map<String, Object>> list(String userId, Integer page, Integer size) {
        List<Map<String, Object>> rows = conversations.selectList(new LambdaQueryWrapper<Conversation>()
                        .eq(userId != null && !userId.isBlank(), Conversation::getUserId, userId)
                        .orderByDesc(Conversation::getUpdatedAt))
                .stream()
                .map(this::row)
                .toList();
        return AdminPages.of(rows, page, size);
    }

    /**
     * 一个会话里的消息，按时间升序。
     *
     * @param id 会话主键
     * @return 消息列表
     */
    public List<Map<String, Object>> messages(String id) {
        Conversation conversation = conversations.selectById(id);
        if (conversation == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "会话不存在");
        }
        return messages.selectList(new LambdaQueryWrapper<ConversationMessage>()
                        .eq(ConversationMessage::getConversationId, id))
                .stream()
                .sorted(Comparator.comparing(ConversationMessage::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::message)
                .toList();
    }

    private Map<String, Object> row(Conversation conversation) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", conversation.getId());
        row.put("userId", conversation.getUserId());
        row.put("title", conversation.getTitle());
        row.put("lastIntent", conversation.getLastIntent());
        row.put("updatedAt", conversation.getUpdatedAt());
        return row;
    }

    private Map<String, Object> message(ConversationMessage message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", message.getId());
        row.put("role", message.getRole());
        row.put("content", message.getContent());
        row.put("intent", message.getIntent());
        row.put("createdAt", message.getCreatedAt());
        return row;
    }
}
