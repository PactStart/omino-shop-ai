package com.omino.shopai.app.service.meta;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.InboxMessage;
import com.omino.shopai.enums.RichTextFormat;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.InboxMessageMapper;
import com.omino.shopai.support.Ids;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 站内信在这里校验归属并写入已读时间。
 */
@Service
public class InboxServiceImpl implements IInboxService {

    private final InboxMessageMapper messages;
    private final Clock clock;

    /**
     * 注入站内信表和时钟。发送和已读都用这一份时钟。
     *
     * @param messages 站内信表
     * @param clock 写入和已读时间
     */
    public InboxServiceImpl(InboxMessageMapper messages, Clock clock) {
        this.messages = messages;
        this.clock = clock;
    }

    /**
     * 写入一条纯文本站内信。用户编号为空则拒绝。
     *
     * @param userId 接收人
     * @param title 标题
     * @param body 正文
     * @return 已写入的站内信
     */
    @Override
    public InboxMessage send(String userId, String title, String body) {
        if (userId == null || userId.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "userId 不能为空");
        }
        InboxMessage message = new InboxMessage();
        message.setId(Ids.ulid());
        message.setUserId(userId);
        message.setTitle(title);
        message.setFormat(RichTextFormat.PLAIN);
        message.setBody(body);
        message.setCreatedAt(OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        insertInbox(message);
        return message;
    }

    /**
     * 列出该用户的站内信，新的在前。
     *
     * @param userId 接收人
     * @return 该用户的站内信
     */
    @Override
    public List<InboxMessage> list(String userId) {
        return inbox(userId);
    }

    /**
     * 数出该用户尚未阅读的站内信。
     *
     * @param userId 接收人
     * @return 未读条数
     */
    @Override
    public long unread(String userId) {
        return inbox(userId).stream().filter(row -> row.getReadAt() == null).count();
    }

    /**
     * 把指定消息标成已读。消息不存在或不属于该用户则拒绝。
     *
     * @param userId 当前用户
     * @param id 站内信主键
     */
    @Override
    public void markRead(String userId, String id) {
        InboxMessage message = findInbox(id);
        if (message == null || !userId.equals(message.getUserId())) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "消息不存在");
        }
        message.setReadAt(OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        saveInbox(message);
    }

    /**
     * 插入一条站内信。
     *
     * @param message 站内信
     */
    protected void insertInbox(InboxMessage message) {
        messages.insert(message);
    }

    /**
     * 按主键更新站内信。已读时间在调用方写好。
     *
     * @param message 站内信
     */
    protected void saveInbox(InboxMessage message) {
        messages.updateById(message);
    }

    /**
     * 按创建时间倒序列出该用户的站内信。
     *
     * @param userId 接收人
     * @return 该用户的站内信
     */
    protected List<InboxMessage> inbox(String userId) {
        return messages.selectList(new LambdaQueryWrapper<InboxMessage>()
                .eq(InboxMessage::getUserId, userId)
                .orderByDesc(InboxMessage::getCreatedAt));
    }

    /**
     * 按主键读取站内信。
     *
     * @param id 站内信主键
     * @return 站内信；没有则空
     */
    protected InboxMessage findInbox(String id) {
        return messages.selectById(id);
    }
}
