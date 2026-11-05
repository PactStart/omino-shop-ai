package com.omino.shopai.app.service.meta;

import com.omino.shopai.entity.InboxMessage;

import java.util.List;

/**
 * 站内信的发送、列表和已读。
 */
public interface IInboxService {

    /**
     * 写入一条纯文本站内信。用户编号为空则拒绝。
     *
     * @param userId 接收人
     * @param title 标题
     * @param body 正文
     * @return 已写入的站内信
     */
    InboxMessage send(String userId, String title, String body);

    /**
     * 列出该用户的站内信，新的在前。
     *
     * @param userId 接收人
     * @return 该用户的站内信
     */
    List<InboxMessage> list(String userId);

    /**
     * 数出该用户尚未阅读的站内信。
     *
     * @param userId 接收人
     * @return 未读条数
     */
    long unread(String userId);

    /**
     * 把指定消息标成已读。消息不存在或不属于该用户则拒绝。
     *
     * @param userId 当前用户
     * @param id 站内信主键
     */
    void markRead(String userId, String id);
}
