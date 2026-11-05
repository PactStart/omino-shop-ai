package com.omino.shopai.exception;
/**
 * 联盟调用失败。响应码用 50010，不带密钥。
 */

public class PlatformCallException extends RuntimeException {

    /**
     * 没有更具体的原因时使用固定说明。
     */
    public PlatformCallException() {
        super("平台查询失败");
    }

    /**
     * 带上这次失败的原因。空白则退回固定说明。
     *
     * @param message 失败原因
     */
    public PlatformCallException(String message) {
        super(text(message));
    }

    /**
     * 保留原始异常，说明用它的消息。
     *
     * @param cause 原始异常
     */
    public PlatformCallException(Throwable cause) {
        super(text(cause == null ? null : cause.getMessage()), cause);
    }

    /**
     * 把非空片段拼成一句原因。全空则退回固定说明。
     *
     * @param parts 错误码和说明
     * @return 拼好的原因
     */
    public static String explain(String... parts) {
        StringBuilder out = new StringBuilder();
        if (parts != null) {
            for (String part : parts) {
                if (part == null || part.isBlank()) {
                    continue;
                }
                if (!out.isEmpty()) {
                    out.append(' ');
                }
                out.append(part.trim());
            }
        }
        return text(out.toString());
    }

    private static String text(String message) {
        return message == null || message.isBlank() ? "平台查询失败" : message;
    }
}
