package com.omino.shopai.app.web.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.service.auth.LoginSession;
import com.omino.shopai.app.service.auth.SmsAuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
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
 * 发送登录验证码。频次和手机号在服务里校验。
 */

    @Operation(summary = "提交 /send")
    @PostMapping("/send")
    public ApiResponse<Void> send(@RequestBody SmsSendRequest request) {
        smsAuthService.send(request.phone());
        return ApiResponse.ok(null, traceId(), now());
    }

    @Operation(summary = "提交 /login")
    @PostMapping("/login")
    public ApiResponse<LoginSession> login(@RequestBody SmsLoginRequest request) {
        return ApiResponse.ok(
                smsAuthService.login(request.phone(), request.code(), request.inviteCode()),
                traceId(),
                now());
    }

    private static String traceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String now() {
        return OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString();
    }
/**
 * 要发送验证码的手机号。
 */

    public record SmsSendRequest(String phone) {
    }
/**
 * 手机号和验证码。
 */

    public record SmsLoginRequest(String phone, String code, String inviteCode) {
    }
}
