package com.omino.shopai.app.adapter.platform;

/**
 * 出站前的密钥闸门。缺任何一个必需密钥就停，不创建官方客户端。
 */
public final class Upstream {

    private Upstream() {
    }

    /**
     * 密钥都非空白才返回。空白或 null 视为未配置。
     */
    public static void call(String... secrets) {
        if (secrets == null) {
            throw new PlatformCallException();
        }
        for (String secret : secrets) {
            if (secret == null || secret.isBlank()) {
                throw new PlatformCallException();
            }
        }
    }
}
