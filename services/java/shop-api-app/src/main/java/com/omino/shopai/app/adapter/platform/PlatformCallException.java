package com.omino.shopai.app.adapter.platform;
/**
 * 联盟调用失败。响应码用 50010，不带密钥。
 */

public class PlatformCallException extends RuntimeException {

    public PlatformCallException() {
        super("平台查询失败");
    }
}
