package com.omino.shopai.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
/**
 * 把异常收成信封。未知异常不泄漏堆栈。
 */

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
/**
 * 字段错误收成 40001，指出字段和原因，不带堆栈。
 */

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " 不能为空")
                .orElse("参数校验失败");
        return ResponseEntity.ok(fail(ErrorCodes.VALIDATION, message));
    }

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<ApiResponse<Void>> unauthenticated(UnauthenticatedException ex) {
        return ResponseEntity.status(401).body(fail(ErrorCodes.UNAUTHENTICATED, ex.getMessage()));
    }

    @ExceptionHandler(ApiBusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(ApiBusinessException ex) {
        return ResponseEntity.ok(fail(ex.code(), ex.getMessage(), ex.field(), ex.reason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unknown(Exception ex) {
        return ResponseEntity.ok(fail(ErrorCodes.INTERNAL, "内部错误"));
    }

    private static ApiResponse<Void> fail(int code, String message) {
        return fail(code, message, null, null);
    }
/**
 * 未分类异常收成 50000，文案固定为内部错误。
 */

    private static ApiResponse<Void> fail(int code, String message, String field, String reason) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String ts = OffsetDateTime.now(SHANGHAI).toString();
        ApiError error = field == null && reason == null ? null : new ApiError(field, reason);
        return new ApiResponse<>(code, message, null, traceId, ts, error);
    }
}
