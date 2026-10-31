package com.omino.shopai.app.web.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.web.app.dto.SmsLoginRequestDto;
import com.omino.shopai.app.web.app.dto.SmsSendRequestDto;
import com.omino.shopai.app.dto.LoginSessionDto;
import com.omino.shopai.app.service.auth.SmsAuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuthController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "AuthController")
@RequestMapping("/v1/auth/sms")
public class AuthController {

    private final SmsAuthService smsAuthService;

    public AuthController(SmsAuthService smsAuthService) {
        this.smsAuthService = smsAuthService;
    }
    /**
     * 向手机号发送登录验证码。
     *
     * @param request 手机号
     * @return 空信封
     */
    @Operation(summary = "发送登录验证码")
    @PostMapping("/send")
    public ApiResponse<Void> send(@RequestBody SmsSendRequestDto request) {
        smsAuthService.send(request.phone());
        return ApiResponse.ok(null);
    }
    /**
     * 用手机号和验证码登录，可带邀请码。
     *
     * @param request 手机号、验证码和邀请码
     * @return 登录会话信封
     */
    @Operation(summary = "用验证码登录")
    @PostMapping("/login")
    public ApiResponse<LoginSessionDto> login(@RequestBody SmsLoginRequestDto request) {
        return ApiResponse.ok(
                smsAuthService.login(request.phone(), request.code(), request.inviteCode()));
    }
}
