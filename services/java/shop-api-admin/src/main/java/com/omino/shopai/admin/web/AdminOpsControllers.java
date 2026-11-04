package com.omino.shopai.admin.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.dto.PageDto;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.admin.web.dto.InboxRequestDto;
import com.omino.shopai.admin.web.dto.RejectRequestDto;
import com.omino.shopai.admin.web.dto.SuggestionRequestDto;
import com.omino.shopai.admin.service.ops.AdminDirectory;
import com.omino.shopai.admin.service.ops.AdminUserGoodsService;
import com.omino.shopai.admin.service.ops.AdminCreditService;
import com.omino.shopai.admin.web.dto.CreditAdjustDto;
import com.omino.shopai.admin.service.wallet.AdminWithdrawalService;
import com.omino.shopai.entity.Announcement;
import com.omino.shopai.entity.AppOrder;
import com.omino.shopai.entity.AppRelease;
import com.omino.shopai.entity.AppRuntimeConfig;
import com.omino.shopai.entity.ChatSuggestion;
import com.omino.shopai.entity.CreditOrder;
import com.omino.shopai.entity.CreditPackage;
import com.omino.shopai.entity.GoodsCollection;
import com.omino.shopai.entity.InboxMessage;
import com.omino.shopai.entity.InvitePosterTemplate;
import com.omino.shopai.support.withdrawal.PayoutViewDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
/**
 * AdminUserController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "AdminUserController")
@RequestMapping("/v1/users")
class AdminUserController {
    private final AdminDirectory directory;
    private final AdminUserGoodsService goods;

    /**
     * 注入用户档案和商品只读。
     *
     * @param directory 用户档案
     * @param goods 授权和商品
     */
    AdminUserController(AdminDirectory directory, AdminUserGoodsService goods) {
        this.directory = directory;
        this.goods = goods;
    }

    /**
     * 按昵称、手机、邀请码、主键和状态分页查用户。列表里的手机已脱敏。
     *
     * @param keyword 昵称包含
     * @param phone 完整手机，精确匹配
     * @param inviteCode 邀请码
     * @param id 用户主键
     * @param status 账号状态 1 或 0
     * @param page 页码
     * @param size 页大小
     * @return 用户页
     */
    @Operation(summary = "分页查询用户")
    @GetMapping
    ApiResponse<PageDto<Map<String, Object>>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "inviteCode", required = false) String inviteCode,
            @RequestParam(value = "id", required = false) String id,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(directory.users(keyword, phone, inviteCode, id, status, page, size));
    }

    /**
     * 查看用户档案。手机是完整号码。授权状态不含推广位。
     *
     * @param id 用户主键
     * @return 档案
     */
    @Operation(summary = "查看用户档案")
    @GetMapping("/{id}")
    ApiResponse<Map<String, Object>> detail(@PathVariable String id) {
        Map<String, Object> body = directory.user(id);
        body.put("authorizations", goods.authorizations(id));
        return ApiResponse.ok(body);
    }

    /**
     * 分页查看该用户的浏览。
     *
     * @param id 用户主键
     * @param page 页码
     * @param size 页大小
     * @return 浏览页
     */
    @Operation(summary = "查看用户浏览记录")
    @GetMapping("/{id}/goods/history")
    ApiResponse<PageDto<Map<String, Object>>> history(
            @PathVariable String id,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(goods.history(id, page, size));
    }

    /**
     * 分页查看该用户的收藏。
     *
     * @param id 用户主键
     * @param page 页码
     * @param size 页大小
     * @return 收藏页
     */
    @Operation(summary = "查看用户收藏")
    @GetMapping("/{id}/goods/favorites")
    ApiResponse<PageDto<Map<String, Object>>> favorites(
            @PathVariable String id,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(goods.favorites(id, page, size));
    }

    /**
     * 查看该用户的合集。
     *
     * @param id 用户主键
     * @return 合集列表
     */
    @Operation(summary = "查看用户合集")
    @GetMapping("/{id}/goods/collections")
    ApiResponse<List<GoodsCollection>> collections(@PathVariable String id) {
        return ApiResponse.ok(goods.collections(id));
    }

    /**
     * 查看合集里尚未移除的商品。
     *
     * @param id 用户主键
     * @param cid 合集主键
     * @return 商品行
     */
    @Operation(summary = "查看合集内商品")
    @GetMapping("/{id}/goods/collections/{cid}")
    ApiResponse<List<Map<String, Object>>> collectionItems(@PathVariable String id, @PathVariable String cid) {
        return ApiResponse.ok(goods.collectionItems(id, cid));
    }

    /**
     * 分页查看该用户邀请的人。手机已脱敏。
     *
     * @param id 邀请人主键
     * @param page 页码
     * @param size 页大小
     * @return 邀请记录页
     */
    @Operation(summary = "查看用户邀请记录")
    @GetMapping("/{id}/invites")
    ApiResponse<PageDto<Map<String, Object>>> invites(
            @PathVariable String id,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(goods.invites(id, page, size));
    }
}
/**
 * AdminOrderController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "AdminOrderController")
@RequestMapping("/v1/orders")
class AdminOrderController {
    private final AdminDirectory directory;

    AdminOrderController(AdminDirectory directory) {
        this.directory = directory;
    }

    /**
     * 按平台、平台订单号、订单状态、结算状态和用户分页查订单。
     *
     * @param userId 用户主键
     * @param platform 平台码
     * @param platformOrderId 平台订单号，精确匹配
     * @param orderStatus 订单状态
     * @param settleStatus 结算状态
     * @param page 页码
     * @param size 页大小
     * @return 订单页
     */
    @Operation(summary = "分页查询电商订单")
    @GetMapping
    ApiResponse<PageDto<Map<String, Object>>> list(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "platformOrderId", required = false) String platformOrderId,
            @RequestParam(value = "orderStatus", required = false) String orderStatus,
            @RequestParam(value = "settleStatus", required = false) String settleStatus,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(directory.orders(userId, platform, platformOrderId, orderStatus, settleStatus, page, size));
    }

    /**
     * 查看一笔订单。没有物流时该字段为空。
     *
     * @param id 订单主键
     * @return 订单
     */
    @Operation(summary = "查看一笔电商订单")
    @GetMapping("/{id}")
    ApiResponse<Map<String, Object>> detail(@PathVariable String id) {
        return ApiResponse.ok(directory.order(id));
    }
}
/**
 * AdminWithdrawalController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "AdminWithdrawalController")
@RequestMapping("/v1/withdrawals")
class AdminWithdrawalController {
    private final AdminDirectory directory;
    private final AdminWithdrawalService withdrawals;

    /**
     * 注入提现查询和打款。
     *
     * @param directory 只读目录
     * @param withdrawals 打款与拒绝
     */
    AdminWithdrawalController(AdminDirectory directory, AdminWithdrawalService withdrawals) {
        this.directory = directory;
        this.withdrawals = withdrawals;
    }

    /**
     * 分页查询提现。响应只有掩码账号。
     *
     * @param userId 用户主键
     * @param status 状态码
     * @param page 页码
     * @param size 页大小
     * @return 提现页
     */
    @Operation(summary = "分页查询提现")
    @GetMapping
    ApiResponse<PageDto<Map<String, Object>>> list(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(directory.withdrawals(userId, status, page, size));
    }

    /**
     * 对一笔待审提现发起打款。打款失败时信封仍是成功，文案说明冻结未动。
     *
     * @param id 提现主键
     * @return 打款结果；失败时只有 payoutResult
     */
    @Operation(summary = "对一笔提现打款")
    @PostMapping("/{id}/payout")
    ApiResponse<Map<String, String>> payout(@PathVariable String id) {
        PayoutViewDto view = withdrawals.payout(id);
        Map<String, String> data = Map.of("payoutResult", view.payoutResult(), "status", view.status() == null ? "" : view.status());
        if ("failed".equals(view.payoutResult())) {
            return new ApiResponse<>(0, view.message(), Map.of("payoutResult", "failed"));
        }
        return ApiResponse.ok(data);
    }
    /**
     * 拒绝一笔提现并解开冻结。
     *
     * @param id 提现主键
     * @param request 拒绝原因
     * @return 空
     */
    @Operation(summary = "拒绝一笔提现")
    @PostMapping("/{id}/reject")
    ApiResponse<Void> reject(@PathVariable String id, @RequestBody RejectRequestDto request) {
        withdrawals.reject(id, request.reason());
        return ApiResponse.ok(null);
    }
}
/**
 * AdminCreditController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "AdminCreditController")
@RequestMapping("/v1/credit-packages")
class AdminCreditController {
    private final AdminDirectory directory;

    AdminCreditController(AdminDirectory directory) {
        this.directory = directory;
    }

    /**
     * 列出积分套餐，含已下架。
     *
     * @return 套餐列表
     */
    @Operation(summary = "列出积分套餐")
    @GetMapping
    ApiResponse<List<CreditPackage>> list() {
        return ApiResponse.ok(directory.packages());
    }

    /**
     * 新建或修改套餐。下架用 status=retired，不物理删除。
     *
     * @param pack 套餐
     * @return 保存后的套餐
     */
    @Operation(summary = "保存积分套餐")
    @PostMapping
    ApiResponse<CreditPackage> save(@RequestBody CreditPackage pack) {
        return ApiResponse.ok(directory.savePackage(pack));
    }

    /**
     * 修改套餐，包括下架。
     *
     * @param id 套餐主键
     * @param pack 套餐
     * @return 保存后的套餐
     */
    @Operation(summary = "修改积分套餐")
    @PatchMapping("/{id}")
    ApiResponse<CreditPackage> patch(@PathVariable String id, @RequestBody CreditPackage pack) {
        pack.setId(id);
        return ApiResponse.ok(directory.savePackage(pack));
    }
}
/**
 * AdminInvitePosterController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "AdminInvitePosterController")
@RequestMapping("/v1/invite-posters")
class AdminInvitePosterController {
    private final AdminDirectory directory;

    AdminInvitePosterController(AdminDirectory directory) {
        this.directory = directory;
    }

    /**
     * 列出海报模板。不在服务端合成预览图。
     *
     * @return 模板列表
     */
    @Operation(summary = "列出邀请海报模板")
    @GetMapping
    ApiResponse<List<InvitePosterTemplate>> list() {
        return ApiResponse.ok(directory.posters());
    }

    /**
     * 新建海报模板。结束时间不晚于开始时间则拒绝。
     *
     * @param poster 模板
     * @return 保存后的模板
     */
    @Operation(summary = "新建邀请海报模板")
    @PostMapping
    ApiResponse<InvitePosterTemplate> save(@RequestBody InvitePosterTemplate poster) {
        return ApiResponse.ok(directory.savePoster(poster));
    }

    /**
     * 修改海报模板。结束时间不晚于开始时间则拒绝。
     *
     * @param id 模板主键
     * @param poster 模板
     * @return 保存后的模板
     */
    @Operation(summary = "修改邀请海报模板")
    @PatchMapping("/{id}")
    ApiResponse<InvitePosterTemplate> patch(@PathVariable String id, @RequestBody InvitePosterTemplate poster) {
        poster.setId(id);
        return ApiResponse.ok(directory.savePoster(poster));
    }
}
/**
 * AdminReleaseController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "AdminReleaseController")
@RequestMapping("/v1/releases")
class AdminReleaseController {
    private final AdminDirectory directory;

    AdminReleaseController(AdminDirectory directory) {
        this.directory = directory;
    }

    /**
     * 列出全部版本。
     *
     * @return 版本列表
     */
    @Operation(summary = "列出版本")
    @GetMapping
    ApiResponse<List<AppRelease>> list() {
        return ApiResponse.ok(directory.releases());
    }

    /**
     * 新建版本。同一天同端同渠道已有行则拒绝。
     *
     * @param release 版本
     * @return 保存后的版本
     */
    @Operation(summary = "发布一个版本")
    @PostMapping
    ApiResponse<AppRelease> save(@RequestBody AppRelease release) {
        return ApiResponse.ok(directory.saveRelease(release));
    }

    /**
     * 修改已有版本，而不是再插同一天的第二行。
     *
     * @param id 版本主键
     * @param release 版本
     * @return 保存后的版本
     */
    @Operation(summary = "修改一个版本")
    @PatchMapping("/{id}")
    ApiResponse<AppRelease> patch(@PathVariable String id, @RequestBody AppRelease release) {
        release.setId(id);
        return ApiResponse.ok(directory.saveRelease(release));
    }
}
/**
 * AdminRuntimeConfigController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "AdminRuntimeConfigController")
@RequestMapping("/v1/runtime-configs")
class AdminRuntimeConfigController {
    private final AdminDirectory directory;

    AdminRuntimeConfigController(AdminDirectory directory) {
        this.directory = directory;
    }

    /**
     * 列出特殊配置。
     *
     * @return 配置列表
     */
    @Operation(summary = "列出特殊配置")
    @GetMapping
    ApiResponse<List<AppRuntimeConfig>> list() {
        return ApiResponse.ok(directory.configs());
    }

    /**
     * 新建特殊配置。
     *
     * @param config 配置
     * @return 保存后的配置
     */
    @Operation(summary = "新建特殊配置")
    @PostMapping
    ApiResponse<AppRuntimeConfig> save(@RequestBody AppRuntimeConfig config) {
        return ApiResponse.ok(directory.saveConfig(config));
    }

    /**
     * 修改特殊配置。
     *
     * @param id 配置主键
     * @param config 配置
     * @return 保存后的配置
     */
    @Operation(summary = "修改特殊配置")
    @PatchMapping("/{id}")
    ApiResponse<AppRuntimeConfig> patch(@PathVariable String id, @RequestBody AppRuntimeConfig config) {
        config.setId(id);
        return ApiResponse.ok(directory.saveConfig(config));
    }
}
/**
 * AdminAnnouncementController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "AdminAnnouncementController")
@RequestMapping("/v1/announcements")
class AdminAnnouncementController {
    private final AdminDirectory directory;

    AdminAnnouncementController(AdminDirectory directory) {
        this.directory = directory;
    }

    /**
     * 列出公告。
     *
     * @return 公告列表
     */
    @Operation(summary = "列出公告")
    @GetMapping
    ApiResponse<List<Announcement>> list() {
        return ApiResponse.ok(directory.announcements());
    }

    /**
     * 新建公告。
     *
     * @param announcement 公告
     * @return 保存后的公告
     */
    @Operation(summary = "新建公告")
    @PostMapping
    ApiResponse<Announcement> save(@RequestBody Announcement announcement) {
        return ApiResponse.ok(directory.saveAnnouncement(announcement));
    }

    /**
     * 修改公告。
     *
     * @param id 公告主键
     * @param announcement 公告
     * @return 保存后的公告
     */
    @Operation(summary = "修改公告")
    @PatchMapping("/{id}")
    ApiResponse<Announcement> patch(@PathVariable String id, @RequestBody Announcement announcement) {
        announcement.setId(id);
        return ApiResponse.ok(directory.saveAnnouncement(announcement));
    }
}
/**
 * AdminInboxController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "AdminInboxController")
@RequestMapping("/v1/inbox")
class AdminInboxController {
    private final AdminDirectory directory;

    AdminInboxController(AdminDirectory directory) {
        this.directory = directory;
    }

    /**
     * 按用户查看站内信。
     *
     * @param userId 用户主键，可空
     * @return 站内信
     */
    @Operation(summary = "查看站内信")
    @GetMapping
    ApiResponse<List<InboxMessage>> list(@RequestParam(value = "userId", required = false) String userId) {
        return ApiResponse.ok(directory.inbox(userId));
    }

    /**
     * 发给一个用户。没有接收人则拒绝。
     *
     * @param request 接收人、标题、正文和格式
     * @return 新站内信
     */
    @Operation(summary = "给一个用户发站内信")
    @PostMapping
    ApiResponse<InboxMessage> send(@RequestBody InboxRequestDto request) {
        return ApiResponse.ok(directory.sendInbox(request.userId(), request.title(), request.body(), request.format()));
    }
}
/**
 * AdminChatSuggestionController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "AdminChatSuggestionController")
@RequestMapping("/v1/chat-suggestions")
class AdminChatSuggestionController {
    private final AdminDirectory directory;

    AdminChatSuggestionController(AdminDirectory directory) {
        this.directory = directory;
    }

    /**
     * 列出全部推荐，包含已下架。
     *
     * @return 推荐列表
     */
    @Operation(summary = "列出推荐问题")
    @GetMapping
    ApiResponse<List<ChatSuggestion>> list() {
        return ApiResponse.ok(directory.suggestions());
    }

    /**
     * 新增一条推荐。上架后超过 12 条则拒绝。
     *
     * @param request 文案
     * @return 新推荐
     */
    @Operation(summary = "新增推荐问题")
    @PostMapping
    ApiResponse<ChatSuggestion> add(@RequestBody SuggestionRequestDto request) {
        return ApiResponse.ok(directory.addSuggestion(request.text()));
    }

    /**
     * 修改文案、排序或上下架。
     *
     * @param id 主键
     * @param request 要改的字段
     * @return 修改后的推荐
     */
    @Operation(summary = "修改推荐问题")
    @PatchMapping("/{id}")
    ApiResponse<ChatSuggestion> patch(@PathVariable String id, @RequestBody SuggestionRequestDto request) {
        return ApiResponse.ok(directory.patchSuggestion(id, request.text(), request.sort(), request.status()));
    }
}

/**
 * 积分比例、支付单、流水和调账。
 */
@RestController
@Tag(name = "积分")
class AdminCreditReadController {
    private final AdminCreditService credits;

    /**
     * 注入积分用例。
     *
     * @param credits 积分服务
     */
    AdminCreditReadController(AdminCreditService credits) {
        this.credits = credits;
    }

    /**
     * 读取当前 token 与积分的比例。不能在这里修改。
     *
     * @return tokensPerCredit
     */
    @Operation(summary = "查看积分比例")
    @GetMapping("/v1/credit-rate")
    ApiResponse<Map<String, String>> rate() {
        return ApiResponse.ok(credits.rate());
    }

    /**
     * 分页查看积分支付单。
     *
     * @param userId 用户主键
     * @param page 页码
     * @param size 页大小
     * @return 支付单页
     */
    @Operation(summary = "查看积分支付单")
    @GetMapping("/v1/credit-orders")
    ApiResponse<PageDto<CreditOrder>> orders(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(credits.orders(userId, page, size));
    }

    /**
     * 分页查看积分流水。
     *
     * @param userId 用户主键
     * @param page 页码
     * @param size 页大小
     * @return 流水页
     */
    @Operation(summary = "查看积分流水")
    @GetMapping("/v1/credits/ledger")
    ApiResponse<PageDto<Map<String, Object>>> ledger(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(credits.ledger(userId, page, size));
    }

    /**
     * 调账。备注为空则拒绝，并写审计。
     *
     * @param request 当前管理会话
     * @param body 用户、金额和备注
     * @return 新流水
     */
    @Operation(summary = "调整用户积分")
    @PostMapping("/v1/credits/adjust")
    ApiResponse<com.omino.shopai.entity.CreditLedger> adjust(HttpServletRequest request, @RequestBody CreditAdjustDto body) {
        Object sub = request.getSession(false) == null ? null : request.getSession(false).getAttribute("ssoSub");
        return ApiResponse.ok(credits.adjust(body.userId(), body.amount(), body.memo(), sub == null ? "admin" : sub.toString()));
    }
}
