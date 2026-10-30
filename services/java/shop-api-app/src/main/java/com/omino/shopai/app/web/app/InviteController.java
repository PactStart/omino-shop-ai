package com.omino.shopai.app.web.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.service.invite.InvitePosterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
/**
 * InviteController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "InviteController")
@RequestMapping("/v1/invite")
public class InviteController {

    private final InvitePosterService invites;

    public InviteController(InvitePosterService invites) {
        this.invites = invites;
    }

    @Operation(summary = "查询")
    @GetMapping
    public ApiResponse<InvitePosterService.InviteView> invite(@RequestAttribute("userId") String userId) {
        return ApiResponse.ok(invites.invite(userId), trace(), now());
    }

    @Operation(summary = "查询 /posters")
    @GetMapping("/posters")
    public ApiResponse<List<InvitePosterService.PosterView>> posters() {
        return ApiResponse.ok(invites.posters(), trace(), now());
    }

    @Operation(summary = "查询 /lookup")
    @GetMapping("/lookup")
    public ApiResponse<Map<String, Boolean>> lookup(@RequestParam("code") String code) {
        return ApiResponse.ok(invites.lookup(code), trace(), now());
    }

    private static String trace() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String now() {
        return OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString();
    }
}
