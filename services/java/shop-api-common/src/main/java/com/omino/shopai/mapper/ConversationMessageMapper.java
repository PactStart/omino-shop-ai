package com.omino.shopai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omino.shopai.entity.ConversationMessage;
import org.apache.ibatis.annotations.Insert;

public interface ConversationMessageMapper extends BaseMapper<ConversationMessage> {

    @Insert("""
            INSERT INTO conversation_message
                (id, conversation_id, client_msg_id, role, content, intent, payload, created_at)
            VALUES
                (#{id}, #{conversationId}, #{clientMsgId}, #{role}, #{content}, #{intent},
                 CAST(#{payload} AS jsonb), #{createdAt})
            """)
    int insertMessage(ConversationMessage message);
}
