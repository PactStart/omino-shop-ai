package com.omino.shopai.api;

import com.omino.shopai.exception.UnauthenticatedException;

import com.omino.shopai.exception.ApiBusinessException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 把异常收成信封。未知异常不泄漏堆栈。
 */

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
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
        // 堆栈只留在服务端。响应仍是固定文案，不带官方原文和密钥。
        log.error("未分类异常", ex);
        return ResponseEntity.ok(fail(ErrorCodes.INTERNAL, "内部错误"));
    }

    private static ApiResponse<Void> fail(int code, String message) {
        return fail(code, message, null, null);
    }
/**
 * 未分类异常收成 50000，文案固定为内部错误。
 */

    /**
     * 失败信封。追踪号和时间先空着，写出前和成功响应用同一次请求的号。
     *
     * @param code 业务码
     * @param message 给人看的说明
     * @param field 出错字段，可空
     * @param reason 字段错误原因，可空
     * @return 失败信封
     */
    private static ApiResponse<Void> fail(int code, String message, String field, String reason) {
        ApiError error = field == null && reason == null ? null : new ApiError(field, reason);
        return new ApiResponse<>(code, message, null, null, null, error);
    }
}
