package com.omino.shopai.app.service.meta;

import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.InboxMessage;
import com.omino.shopai.support.Ids;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
/**
 * InboxService 编排用例，事务和校验放在这里。
 */

@Service
public class InboxService {

    private final IMeta book;
    private final Clock clock;
/**
 * 注入站内信存储。发送和已读都走这里。
 */

    public InboxService(IMeta book, Clock clock) {
        this.book = book;
        this.clock = clock;
    }
/**
 * 写入一条站内信。接收人必须是已有用户。
 */

    public InboxMessage send(String userId, String title, String body) {
        if (userId == null || userId.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "userId 不能为空");
        }
        InboxMessage message = new InboxMessage();
        message.setId(Ids.ulid());
        message.setUserId(userId);
        message.setTitle(title);
        message.setBody(body);
        message.setCreatedAt(OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        book.insertInbox(message);
        return message;
    }

    public List<InboxMessage> list(String userId) {
        return book.inbox(userId);
    }

    public long unread(String userId) {
        return book.inbox(userId).stream().filter(row -> row.getReadAt() == null).count();
    }
/**
 * 把指定消息标成已读。不是本人的消息则拒绝。
 */

    public void markRead(String userId, String id) {
        InboxMessage message = book.findInbox(id);
        if (message == null || !userId.equals(message.getUserId())) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "消息不存在");
        }
        message.setReadAt(OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        book.saveInbox(message);
    }
}
