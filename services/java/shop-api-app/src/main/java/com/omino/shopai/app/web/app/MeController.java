package com.omino.shopai.app.web.app;

import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.dto.MeViewDto;
import com.omino.shopai.app.service.auth.MeQueryService;
import com.omino.shopai.app.web.app.dto.DeleteAccountDto;
import com.omino.shopai.app.web.app.dto.PushRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户的资料、推送开关和注销。
 */
@RestController
@Tag(name = "MeController")
@RequestMapping("/v1/me")
public class MeController {

    private final MeQueryService me;

    /**
     * 注入资料用例。
     *
     * @param me 资料用例
     */
    public MeController(MeQueryService me) {
        this.me = me;
    }

    /**
     * 读取当前用户资料。
     *
     * @param userId 当前用户
     * @return 资料信封
     */
    @Operation(summary = "读取当前用户资料")
    @GetMapping
    public ApiResponse<MeViewDto> get(@RequestAttribute("userId") String userId) {
        return ApiResponse.ok(me.get(userId));
    }

    /**
     * 修改当前用户的推送开关。
     *
     * @param userId 当前用户
     * @param request 开关
     * @return 空信封
     */
    @Operation(summary = "修改推送开关")
    @PatchMapping("/push")
    public ApiResponse<Void> push(@RequestAttribute("userId") String userId, @RequestBody PushRequestDto request) {
        me.updatePush(userId, request.enabled());
        return ApiResponse.ok(null);
    }

    /**
     * 注销当前用户。上帝视角的令牌不能注销正在查看的账号。
     *
     * @param userId 当前用户
     * @param godView 令牌是否带 gv=1
     * @param request 确认词
     * @return 空信封
     */
    @Operation(summary = "注销当前用户")
    @PostMapping("/delete")
    public ApiResponse<Void> delete(
            @RequestAttribute("userId") String userId,
            @RequestAttribute(name = "godView", required = false) Boolean godView,
            @RequestBody DeleteAccountDto request) {
        me.delete(userId, request.confirm(), Boolean.TRUE.equals(godView));
        return ApiResponse.ok(null);
    }
}
