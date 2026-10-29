package com.omino.shopai.app.adapter.platform;
/**
 * 联盟出站的统一入口。
 */

final class Upstream {

    private Upstream() {
    }
/**
 * 调用联盟前确认适配器已启用。失败收成平台错误，不带密钥。
 */

    static void call(String... secrets) {
        for (String secret : secrets) {
            if (secret == null || secret.isBlank()) {
                throw new PlatformCallException();
            }
        }
        throw new PlatformCallException();
    }
}
