package com.omino.shopai.admin.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.admin.web.dto.InboxRequestDto;
import com.omino.shopai.admin.web.dto.RejectRequestDto;
import com.omino.shopai.admin.web.dto.SuggestionRequestDto;
import com.omino.shopai.admin.service.ops.AdminDirectory;
import com.omino.shopai.admin.service.wallet.AdminWithdrawalService;
import com.omino.shopai.entity.Announcement;
import com.omino.shopai.entity.AppOrder;
import com.omino.shopai.entity.AppRelease;
import com.omino.shopai.entity.AppRuntimeConfig;
import com.omino.shopai.entity.ChatSuggestion;
import com.omino.shopai.entity.CreditPackage;
import com.omino.shopai.entity.InboxMessage;
import com.omino.shopai.entity.InvitePosterTemplate;
import com.omino.shopai.support.withdrawal.PayoutViewDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    AdminUserController(AdminDirectory directory) {
        this.directory = directory;
    }

    @Operation(summary = "查询")
    @GetMapping
    ApiResponse<List<Map<String, Object>>> list(@RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(directory.users(keyword));
    }

    @Operation(summary = "查询 /{id}")
    @GetMapping("/{id}")
    ApiResponse<Map<String, Object>> detail(@PathVariable String id) {
        return ApiResponse.ok(directory.user(id));
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

    @Operation(summary = "查询")
    @GetMapping
    ApiResponse<List<AppOrder>> list() {
        return ApiResponse.ok(directory.orders());
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
 * 审核提现。打款失败时信封仍是成功，文案说明冻结未动。
 */

    AdminWithdrawalController(AdminDirectory directory, AdminWithdrawalService withdrawals) {
        this.directory = directory;
        this.withdrawals = withdrawals;
    }

    @Operation(summary = "查询")
    @GetMapping
    ApiResponse<List<Map<String, Object>>> list(@RequestParam(value = "status", required = false) String status) {
        return ApiResponse.ok(directory.withdrawals(status));
    }

    @Operation(summary = "提交 /{id}/payout")
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
 * 拒绝并解开冻结。
 */

    @Operation(summary = "提交 /{id}/reject")
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

    @Operation(summary = "查询")
    @GetMapping
    ApiResponse<List<CreditPackage>> list() {
        return ApiResponse.ok(directory.packages());
    }

    @Operation(summary = "提交")
    @PostMapping
    ApiResponse<CreditPackage> save(@RequestBody CreditPackage pack) {
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

    @Operation(summary = "查询")
    @GetMapping
    ApiResponse<List<InvitePosterTemplate>> list() {
        return ApiResponse.ok(directory.posters());
    }

    @Operation(summary = "提交")
    @PostMapping
    ApiResponse<InvitePosterTemplate> save(@RequestBody InvitePosterTemplate poster) {
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

    @Operation(summary = "提交")
    @PostMapping
    ApiResponse<AppRelease> save(@RequestBody AppRelease release) {
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

    @Operation(summary = "提交")
    @PostMapping
    ApiResponse<AppRuntimeConfig> save(@RequestBody AppRuntimeConfig config) {
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

    @Operation(summary = "提交")
    @PostMapping
    ApiResponse<Announcement> save(@RequestBody Announcement announcement) {
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

    @Operation(summary = "提交")
    @PostMapping
    ApiResponse<InboxMessage> send(@RequestBody InboxRequestDto request) {
        return ApiResponse.ok(directory.sendInbox(request.userId(), request.title(), request.body()));
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

    @Operation(summary = "提交")
    @PostMapping
    ApiResponse<ChatSuggestion> add(@RequestBody SuggestionRequestDto request) {
        return ApiResponse.ok(directory.addSuggestion(request.text()));
    }
}
