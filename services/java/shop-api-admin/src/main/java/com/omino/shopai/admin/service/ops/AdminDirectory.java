package com.omino.shopai.admin.service.ops;

import com.omino.shopai.dto.PageDto;
import com.omino.shopai.admin.support.AdminPages;
import com.omino.shopai.enums.ActiveStatus;
import com.omino.shopai.enums.OrderStatus;
import com.omino.shopai.enums.Platform;
import com.omino.shopai.enums.RichTextFormat;
import com.omino.shopai.enums.SettleStatus;
import com.omino.shopai.enums.UserStatus;
import com.omino.shopai.enums.WithdrawalStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.admin.service.ops.SuggestionRules;
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
import com.omino.shopai.support.RichText;
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

    public PageDto<Map<String, Object>> users(String keyword, String phone, String inviteCode, String id, String status, Integer page, Integer size) {
        UserStatus parsed = null;
        if (status != null && !status.isBlank()) {
            try {
                parsed = UserStatus.fromCode(Integer.valueOf(status.trim()));
            } catch (RuntimeException ex) {
                throw new ApiBusinessException(ErrorCodes.VALIDATION, "状态不正确");
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AppUser user : users.selectList(null)) {
            if (id != null && !id.isBlank() && !id.equals(user.getId())) {
                continue;
            }
            if (phone != null && !phone.isBlank() && !phone.equals(user.getPhone())) {
                continue;
            }
            if (inviteCode != null && !inviteCode.isBlank() && !inviteCode.equals(user.getInviteCode())) {
                continue;
            }
            if (parsed != null && user.getStatus() != parsed) {
                continue;
            }
            if (keyword != null && !keyword.isBlank()
                    && (user.getNickname() == null || !user.getNickname().contains(keyword))) {
                continue;
            }
            rows.add(userRow(user, true));
        }
        return AdminPages.of(rows, page, size);
    }

    public Map<String, Object> user(String id) {
        AppUser user = users.selectById(id);
        if (user == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "用户不存在");
        }
        return userRow(user, false);
    }

    /**
     * 按筛选条件分页查询订单。金额是两位小数，没有物流字段时保持空。
     *
     * @param userId 用户主键，可空
     * @param platform 平台码，可空
     * @param platformOrderId 平台订单号，可空。精确匹配
     * @param orderStatus 订单状态，可空
     * @param settleStatus 结算状态，可空
     * @param page 页码
     * @param size 页大小
     * @return 订单页
     */
    public PageDto<Map<String, Object>> orders(String userId, String platform, String platformOrderId, String orderStatus, String settleStatus, Integer page, Integer size) {
        Platform parsedPlatform = platform == null || platform.isBlank() ? null : Platform.fromCode(platform);
        String platformOrder = platformOrderId == null ? "" : platformOrderId.trim();
        OrderStatus parsedOrder = orderStatus == null || orderStatus.isBlank() ? null : OrderStatus.fromCode(orderStatus);
        SettleStatus parsedSettle = settleStatus == null || settleStatus.isBlank() ? null : SettleStatus.fromCode(settleStatus);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AppOrder order : orders.selectList(new LambdaQueryWrapper<AppOrder>().orderByDesc(AppOrder::getCreatedAt))) {
            if (userId != null && !userId.isBlank() && !userId.equals(order.getUserId())) {
                continue;
            }
            if (parsedPlatform != null && order.getPlatform() != parsedPlatform) {
                continue;
            }
            if (!platformOrder.isEmpty() && !platformOrder.equals(order.getPlatformOrderId())) {
                continue;
            }
            if (parsedOrder != null && order.getOrderStatus() != parsedOrder) {
                continue;
            }
            if (parsedSettle != null && order.getSettleStatus() != parsedSettle) {
                continue;
            }
            rows.add(orderRow(order));
        }
        return AdminPages.of(rows, page, size);
    }

    /**
     * 一笔订单的运营可见列。不返回联盟原始报文。
     *
     * @param id 订单主键
     * @return 订单
     */
    public Map<String, Object> order(String id) {
        AppOrder order = orders.selectById(id);
        if (order == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "订单不存在");
        }
        return orderRow(order);
    }

    /**
     * 提现分页。只带掩码账号。
     *
     * @param userId 用户主键，可空
     * @param status 状态码，可空
     * @param page 页码
     * @param size 页大小
     * @return 提现页
     */
    public PageDto<Map<String, Object>> withdrawals(String userId, String status, Integer page, Integer size) {
        WithdrawalStatus parsed = status == null || status.isBlank() ? null : WithdrawalStatus.fromCode(status);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Withdrawal row : withdrawals.selectList(new LambdaQueryWrapper<Withdrawal>()
                .eq(userId != null && !userId.isBlank(), Withdrawal::getUserId, userId)
                .eq(parsed != null, Withdrawal::getStatus, parsed)
                .orderByDesc(Withdrawal::getCreatedAt))) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", row.getId());
            view.put("userId", row.getUserId());
            view.put("amount", AdminPages.money(row.getAmount()));
            view.put("status", row.getStatus() == null ? null : row.getStatus().code());
            view.put("channel", row.getChannel() == null ? null : row.getChannel().code());
            view.put("accountMask", row.getAccountMask() == null ? mask(row.getPayeeAccount()) : row.getAccountMask());
            view.put("createdAt", row.getCreatedAt());
            rows.add(view);
        }
        return AdminPages.of(rows, page, size);
    }

    /**
     * 列出全部积分套餐。
     *
     * @return 套餐列表
     */
    public List<CreditPackage> packages() {
        return packages.selectList(null);
    }

    /**
     * 新建或修改积分套餐。新建时同时写入创建时间和更新时间，修改时推进更新时间。
     *
     * @param pack 套餐；没有 id 时新建
     * @return 已保存的套餐
     */
    public CreditPackage savePackage(CreditPackage pack) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (pack.getId() == null) {
            pack.setId(Ids.ulid());
            pack.setCreatedAt(now);
            pack.setUpdatedAt(now);
            packages.insert(pack);
        } else {
            pack.setUpdatedAt(now);
            packages.updateById(pack);
        }
        return pack;
    }

    /**
     * 全部版本。
     *
     * @return 版本列表
     */
    public List<AppRelease> releases() {
        return releases.selectList(null);
    }

    /**
     * 全部特殊配置。
     *
     * @return 配置列表
     */
    public List<AppRuntimeConfig> configs() {
        return configs.selectList(null);
    }

    /**
     * 全部公告。
     *
     * @return 公告列表
     */
    public List<Announcement> announcements() {
        return announcements.selectList(null);
    }

    /**
     * 站内信。可以按用户收窄。
     *
     * @param userId 用户主键，可空
     * @return 站内信
     */
    public List<InboxMessage> inbox(String userId) {
        return inbox.selectList(new LambdaQueryWrapper<InboxMessage>()
                .eq(userId != null && !userId.isBlank(), InboxMessage::getUserId, userId)
                .orderByDesc(InboxMessage::getCreatedAt));
    }

    public List<InvitePosterTemplate> posters() {
        return posters.selectList(null);
    }
/**
 * 保存修改。
 */

    public InvitePosterTemplate savePoster(InvitePosterTemplate poster) {
        if (poster.getStartsAt() != null && poster.getEndsAt() != null && !poster.getEndsAt().isAfter(poster.getStartsAt())) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "结束时间必须晚于开始时间");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (poster.getId() == null) {
            poster.setId(Ids.ulid());
            poster.setCreatedAt(now);
            poster.setUpdatedAt(now);
            posters.insert(poster);
        } else {
            poster.setUpdatedAt(now);
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
        RichText.Normalized notes = RichText.normalize(release.getFormat(), release.getChangelog());
        release.setFormat(notes.format());
        release.setChangelog(notes.body());
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
        RichText.Normalized text = RichText.normalize(announcement.getFormat(), announcement.getBody());
        announcement.setFormat(text.format());
        announcement.setBody(text.body());
        // 端和渠道是 jsonb。空表示全端、全渠道，写成空数组再按 jsonb 绑定。
        announcement.setClientTypes(jsonList(announcement.getClientTypes()));
        announcement.setChannels(jsonList(announcement.getChannels()));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (announcement.getId() == null) {
            announcement.setId(Ids.ulid());
            announcement.setCreatedAt(now);
            announcement.setUpdatedAt(now);
            announcements.insertJson(announcement);
        } else {
            announcement.setUpdatedAt(now);
            announcements.updateJson(announcement);
        }
        return announcement;
    }

    /**
     * 把逗号分隔的端或渠道收成 JSON 数组。空表示不限制。
     *
     * @param raw 逗号分隔文本，或已经是 JSON 数组
     * @return JSON 数组文本
     */
    private static String jsonList(String raw) {
        if (raw == null || raw.isBlank()) {
            return "[]";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            return trimmed;
        }
        StringBuilder builder = new StringBuilder("[");
        boolean any = false;
        for (String part : trimmed.split(",")) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            if (any) {
                builder.append(',');
            }
            builder.append('"').append(item.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            any = true;
        }
        builder.append(']');
        return builder.toString();
    }
/**
 * 代发站内信。接收人必须存在。
 */

    public InboxMessage sendInbox(String userId, String title, String body, RichTextFormat format) {
        if (userId == null || userId.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "userId 不能为空");
        }
        RichText.Normalized text = RichText.normalize(format, body);
        InboxMessage message = new InboxMessage();
        message.setId(Ids.ulid());
        message.setUserId(userId);
        message.setTitle(title);
        message.setFormat(text.format());
        message.setBody(text.body());
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

    /**
     * 全部推荐问题，包含已下架。
     *
     * @return 推荐列表
     */
    public List<ChatSuggestion> suggestions() {
        return SuggestionRules.list(suggestions.selectList(null));
    }

    /**
     * 修改一条推荐。超限时这一条保持原状态。
     *
     * @param id 主键
     * @param text 文案，可空
     * @param sort 排序，可空
     * @param status 状态码，可空
     * @return 修改后的行
     */
    public ChatSuggestion patchSuggestion(String id, String text, Integer sort, String status) {
        List<ChatSuggestion> rows = suggestions.selectList(null);
        ChatSuggestion updated = SuggestionRules.patch(rows, id, text, sort, status);
        suggestions.updateById(updated);
        return updated;
    }

    private static Map<String, Object> orderRow(AppOrder order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("userId", order.getUserId());
        row.put("platform", order.getPlatform() == null ? null : order.getPlatform().code());
        row.put("platformOrderId", order.getPlatformOrderId());
        row.put("goodsId", order.getGoodsId());
        row.put("platformGoodsId", order.getPlatformGoodsId());
        row.put("goodsTitle", order.getGoodsTitle());
        row.put("goodsImageUrl", order.getGoodsImageUrl());
        row.put("payAmount", AdminPages.money(order.getPayAmount()));
        row.put("parentUserId", order.getParentUserId());
        row.put("mediaCommission", AdminPages.money(order.getMediaCommission()));
        row.put("userCommission", AdminPages.money(order.getUserCommission()));
        row.put("orderStatus", order.getOrderStatus() == null ? null : order.getOrderStatus().code());
        row.put("settleStatus", order.getSettleStatus() == null ? null : order.getSettleStatus().code());
        row.put("walletLedgerId", order.getWalletLedgerId());
        row.put("orderedAt", order.getOrderedAt());
        row.put("shippedAt", order.getShippedAt());
        row.put("logistics", null);
        return row;
    }

    private static Map<String, Object> userRow(AppUser user, boolean masked) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("nickname", user.getNickname());
        row.put("phone", masked ? mask(user.getPhone()) : user.getPhone());
        row.put("inviterId", user.getInviterId());
        row.put("avatarUrl", user.getAvatarUrl());
        row.put("locale", user.getLocale());
        row.put("pushEnabled", user.getPushEnabled() == null ? null : user.getPushEnabled().code());
        row.put("inviteCode", user.getInviteCode());
        row.put("status", user.getStatus() == null ? null : user.getStatus().code());
        row.put("createdAt", user.getCreatedAt());
        row.put("updatedAt", user.getUpdatedAt());
        row.put("deletedAt", user.getDeletedAt());
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
