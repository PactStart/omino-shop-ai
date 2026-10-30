package com.omino.shopai.app.service.meta;

import com.omino.shopai.entity.Announcement;
import com.omino.shopai.entity.AppRelease;
import com.omino.shopai.entity.AppRuntimeConfig;
import com.omino.shopai.entity.InboxMessage;

import java.util.List;
/**
 * 版本、配置和公告的存储端口。
 */

public interface IMeta {

    void insertRelease(AppRelease release);

    boolean releaseExists(String clientType, String channel, int versionCode);

    List<AppRelease> publishedReleases(String clientType, String channel);

    List<AppRuntimeConfig> activeConfigs();

    List<Announcement> announcements();

    void insertInbox(InboxMessage message);

    void saveInbox(InboxMessage message);

    List<InboxMessage> inbox(String userId);

    InboxMessage findInbox(String id);
}
