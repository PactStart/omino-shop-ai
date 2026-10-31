package com.omino.shopai.app.web.app;

import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.dto.UnionAuthDto;
import com.omino.shopai.app.service.auth.UnionAuthService;
import com.omino.shopai.app.web.app.dto.AuthPlatformDto;
import com.omino.shopai.exception.ApiBusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消费者查询和取消联盟授权。
 */
@RestController
@Tag(name = "AuthorizationController")
@RequestMapping("/v1/me/authorizations")
public class AuthorizationController {

    private final UnionAuthService auth;

    /**
     * 授权用例。
     *
     * @param auth 查询、取消和淘宝回调
     */
    public AuthorizationController(UnionAuthService auth) {
        this.auth = auth;
    }

    /**
     * 查询一个平台是否已授权。未授权时附带一条授权链接。
     *
     * @param userId 当前用户
     * @param body 平台码
     * @return 授权状态信封
     */
    @Operation(summary = "查询联盟授权状态")
    @PostMapping("/status")
    public ApiResponse<UnionAuthDto> status(
            @RequestAttribute("userId") String userId,
            @RequestBody(required = false) AuthPlatformDto body) {
        return ApiResponse.ok(auth.status(userId, body == null ? null : body.platform()));
    }

    /**
     * 取消一个平台的授权。京东会拒绝。
     *
     * @param userId 当前用户
     * @param body 平台码
     * @return 取消后的状态信封
     */
    @Operation(summary = "取消联盟授权")
    @PostMapping("/revoke")
    public ApiResponse<UnionAuthDto> revoke(
            @RequestAttribute("userId") String userId,
            @RequestBody(required = false) AuthPlatformDto body) {
        return ApiResponse.ok(auth.revoke(userId, body == null ? null : body.platform()));
    }

    /**
     * 淘宝授权完成后的浏览器回调。不要求登录。
     *
     * @param code 授权码
     * @param state 签发链接时的签名
     * @param error 用户拒绝或淘宝返回的错误码
     * @return 备案结果信封
     */
    @Operation(summary = "完成淘宝授权")
    @GetMapping("/taobao/callback")
    public ApiResponse<UnionAuthDto> taobaoCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        if (error != null && !error.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "授权未完成");
        }
        return ApiResponse.ok(auth.completeTaobao(code, state));
    }
}
