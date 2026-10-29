package com.omino.shopai.app.service.meta;

import com.omino.shopai.enums.ActiveStatus;
import com.omino.shopai.enums.PublishStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.entity.Announcement;
import com.omino.shopai.entity.AppRelease;
import com.omino.shopai.entity.AppRuntimeConfig;
import com.omino.shopai.entity.InboxMessage;
import com.omino.shopai.mapper.AnnouncementMapper;
import com.omino.shopai.mapper.AppReleaseMapper;
import com.omino.shopai.mapper.AppRuntimeConfigMapper;
import com.omino.shopai.mapper.InboxMessageMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
/**
 * 持久化边界。事务和状态判断由调用方负责。
 */

@Repository
public class MybatisMetaBook implements MetaBook {

    private final AppReleaseMapper releases;
    private final AppRuntimeConfigMapper configs;
    private final AnnouncementMapper announcements;
    private final InboxMessageMapper inbox;
/**
 * 版本、运行时配置和公告的 MyBatis 访问。
 */

    public MybatisMetaBook(
            AppReleaseMapper releases,
            AppRuntimeConfigMapper configs,
            AnnouncementMapper announcements,
            InboxMessageMapper inbox) {
        this.releases = releases;
        this.configs = configs;
        this.announcements = announcements;
        this.inbox = inbox;
    }

    @Override
    public void insertRelease(AppRelease release) {
        releases.insert(release);
    }
/**
 * 判断该端该渠道是否已有版本行，避免重复发布。
 */

    @Override
    public boolean releaseExists(String clientType, String channel, int versionCode) {
        Long count = releases.selectCount(new LambdaQueryWrapper<AppRelease>()
                .eq(AppRelease::getClientType, clientType)
                .eq(AppRelease::getChannel, channel)
                .eq(AppRelease::getVersionCode, versionCode));
        return count != null && count > 0;
    }

    @Override
    public List<AppRelease> publishedReleases(String clientType, String channel) {
        return releases.selectList(new LambdaQueryWrapper<AppRelease>()
                .eq(AppRelease::getClientType, clientType)
                .eq(AppRelease::getChannel, channel)
                .eq(AppRelease::getStatus, PublishStatus.PUBLISHED));
    }

    @Override
    public List<AppRuntimeConfig> activeConfigs() {
        return configs.selectList(new LambdaQueryWrapper<AppRuntimeConfig>().eq(AppRuntimeConfig::getStatus, ActiveStatus.ACTIVE));
    }

    @Override
    public List<Announcement> announcements() {
        return announcements.selectList(new LambdaQueryWrapper<Announcement>().eq(Announcement::getStatus, PublishStatus.PUBLISHED));
    }

    @Override
    public void insertInbox(InboxMessage message) {
        inbox.insert(message);
    }

    @Override
    public void saveInbox(InboxMessage message) {
        inbox.updateById(message);
    }

    @Override
    public List<InboxMessage> inbox(String userId) {
        return inbox.selectList(new LambdaQueryWrapper<InboxMessage>()
                .eq(InboxMessage::getUserId, userId)
                .orderByDesc(InboxMessage::getCreatedAt));
    }

    @Override
    public InboxMessage findInbox(String id) {
        return inbox.selectById(id);
    }
}
