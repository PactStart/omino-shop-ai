package com.omino.shopai.api;
/**
 * 可预期的业务失败，带约定错误码。
 */

public class ApiBusinessException extends RuntimeException {

    private final int code;
    private final String field;
    private final String reason;

    public ApiBusinessException(int code, String message) {
        this(code, message, null, null);
    }

    public ApiBusinessException(int code, String message, String reason) {
        this(code, message, null, reason);
    }
/**
 * 带错误码的业务失败。消息给调用方，不含堆栈和密钥。
 */

    public ApiBusinessException(int code, String message, String field, String reason) {
        super(message);
        this.code = code;
        this.field = field;
        this.reason = reason;
    }

    public int code() {
        return code;
    }

    public String field() {
        return field;
    }

    public String reason() {
        return reason;
    }
}
