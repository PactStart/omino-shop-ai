package com.omino.shopai.admin.service.ops;

import com.omino.shopai.enums.ActiveStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.Announcement;
import com.omino.shopai.entity.AppOrder;
import com.omino.shopai.entity.AppRelease;
import com.omino.shopai.entity.AppRuntimeConfig;
import com.omino.shopai.entity.AppUser;
import com.omino.shopai.entity.ChatSuggestion;
import com.omino.shopai.entity.CreditPackage;
import com.omino.shopai.entity.InboxMessage;
import com.omino.shopai.entity.InvitePosterTemplate;
import com.omino.shopai.entity.Withdrawal;
import com.omino.shopai.mapper.AnnouncementMapper;
import com.omino.shopai.mapper.AppOrderMapper;
import com.omino.shopai.mapper.AppReleaseMapper;
import com.omino.shopai.mapper.AppRuntimeConfigMapper;
import com.omino.shopai.mapper.AppUserMapper;
import com.omino.shopai.mapper.ChatSuggestionMapper;
import com.omino.shopai.mapper.CreditPackageMapper;
import com.omino.shopai.mapper.InboxMessageMapper;
import com.omino.shopai.mapper.InvitePosterTemplateMapper;
import com.omino.shopai.mapper.WithdrawalMapper;
import com.omino.shopai.support.Ids;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * 运营查询和写入的门面。
 */

@Service
public class AdminDirectory {

    private final AppUserMapper users;
    private final AppOrderMapper orders;
    private final WithdrawalMapper withdrawals;
    private final CreditPackageMapper packages;
    private final InvitePosterTemplateMapper posters;
    private final AppReleaseMapper releases;
    private final AppRuntimeConfigMapper configs;
    private final AnnouncementMapper announcements;
    private final InboxMessageMapper inbox;
    private final ChatSuggestionMapper suggestions;
/**
 * 运营读写的门面。各用例仍在对应服务里。
 */

    public AdminDirectory(
            AppUserMapper users,
            AppOrderMapper orders,
            WithdrawalMapper withdrawals,
            CreditPackageMapper packages,
            InvitePosterTemplateMapper posters,
            AppReleaseMapper releases,
            AppRuntimeConfigMapper configs,
            AnnouncementMapper announcements,
            InboxMessageMapper inbox,
            ChatSuggestionMapper suggestions) {
        this.users = users;
        this.orders = orders;
        this.withdrawals = withdrawals;
        this.packages = packages;
        this.posters = posters;
        this.releases = releases;
        this.configs = configs;
        this.announcements = announcements;
        this.inbox = inbox;
        this.suggestions = suggestions;
    }

    public List<Map<String, Object>> users(String keyword) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AppUser user : users.selectList(null)) {
            if (keyword != null && !keyword.isBlank() && (user.getPhone() == null || !user.getPhone().contains(keyword))
                    && (user.getNickname() == null || !user.getNickname().contains(keyword))) {
                continue;
            }
            rows.add(userRow(user, true));
        }
        return rows;
    }

    public Map<String, Object> user(String id) {
        AppUser user = users.selectById(id);
        if (user == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "用户不存在");
        }
        return userRow(user, false);
    }

    public List<AppOrder> orders() {
        return orders.selectList(null);
    }

    public List<Map<String, Object>> withdrawals(String status) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Withdrawal row : withdrawals.selectList(new LambdaQueryWrapper<Withdrawal>().eq(status != null && !status.isBlank(), Withdrawal::getStatus, status))) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", row.getId());
            view.put("userId", row.getUserId());
            view.put("amount", row.getAmount());
            view.put("status", row.getStatus());
            view.put("channel", row.getChannel());
            view.put("accountMask", row.getAccountMask() == null ? mask(row.getPayeeAccount()) : row.getAccountMask());
            rows.add(view);
        }
        return rows;
    }

    public List<CreditPackage> packages() {
        return packages.selectList(null);
    }
/**
 * 保存修改。
 */

    public CreditPackage savePackage(CreditPackage pack) {
        if (pack.getId() == null) {
            pack.setId(Ids.ulid());
            pack.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            packages.insert(pack);
        } else {
            packages.updateById(pack);
        }
        pack.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return pack;
    }

    public List<InvitePosterTemplate> posters() {
        return posters.selectList(null);
    }
/**
 * 保存修改。
 */

    public InvitePosterTemplate savePoster(InvitePosterTemplate poster) {
        if (poster.getId() == null) {
            poster.setId(Ids.ulid());
            posters.insert(poster);
        } else {
            posters.updateById(poster);
        }
        return poster;
    }
/**
 * 保存修改。
 */

    public AppRelease saveRelease(AppRelease release) {
        Integer code = release.getVersionCode();
        if (code == null || code < 20000101 || code > 20991231) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "版本号不正确");
        }
        Long existing = releases.selectCount(new LambdaQueryWrapper<AppRelease>()
                .eq(AppRelease::getClientType, release.getClientType())
                .eq(AppRelease::getChannel, release.getChannel())
                .eq(AppRelease::getVersionCode, code)
                .ne(release.getId() != null, AppRelease::getId, release.getId()));
        if (existing != null && existing > 0) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "同端同渠道同一天不能插入第二条");
        }
        if (release.getId() == null) {
            release.setId(Ids.ulid());
            releases.insert(release);
        } else {
            releases.updateById(release);
        }
        return release;
    }
/**
 * 保存修改。
 */

    public AppRuntimeConfig saveConfig(AppRuntimeConfig config) {
        if (config.getId() == null) {
            config.setId(Ids.ulid());
            configs.insert(config);
        } else {
            configs.updateById(config);
        }
        return config;
    }
/**
 * 保存修改。
 */

    public Announcement saveAnnouncement(Announcement announcement) {
        if (announcement.getId() == null) {
            announcement.setId(Ids.ulid());
            announcements.insert(announcement);
        } else {
            announcements.updateById(announcement);
        }
        return announcement;
    }
/**
 * 代发站内信。接收人必须存在。
 */

    public InboxMessage sendInbox(String userId, String title, String body) {
        if (userId == null || userId.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "userId 不能为空");
        }
        InboxMessage message = new InboxMessage();
        message.setId(Ids.ulid());
        message.setUserId(userId);
        message.setTitle(title);
        message.setBody(body);
        message.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        inbox.insert(message);
        return message;
    }
/**
 * 新增开场建议。生效条数到达上限则拒绝。
 */

    public ChatSuggestion addSuggestion(String text) {
        Long active = suggestions.selectCount(new LambdaQueryWrapper<ChatSuggestion>().eq(ChatSuggestion::getStatus, ActiveStatus.ACTIVE));
        if (active != null && active >= 12) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "生效推荐不能超过 12 条");
        }
        ChatSuggestion row = new ChatSuggestion();
        row.setId(Ids.ulid());
        row.setText(text);
        row.setStatus(ActiveStatus.ACTIVE);
        row.setSort(active == null ? 0 : active.intValue());
        row.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        row.setUpdatedAt(row.getCreatedAt());
        suggestions.insert(row);
        return row;
    }

    private static Map<String, Object> userRow(AppUser user, boolean masked) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("nickname", user.getNickname());
        row.put("phone", masked ? mask(user.getPhone()) : user.getPhone());
        row.put("inviteCode", user.getInviteCode());
        row.put("status", user.getStatus());
        return row;
    }
/**
 * 脱敏后返回。
 */

    static String mask(String account) {
        if (account == null || account.length() < 7) {
            return "****";
        }
        if (account.matches("^1[3-9]\\d{9}$")) {
            return account.substring(0, 3) + "****" + account.substring(7);
        }
        return account.substring(0, 2) + "****" + account.substring(account.length() - 2);
    }
}
