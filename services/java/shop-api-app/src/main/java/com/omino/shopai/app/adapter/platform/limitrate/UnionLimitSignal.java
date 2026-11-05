package com.omino.shopai.app.adapter.platform.limitrate;

/**
 * 从某个平台的响应或异常里认出限流。各平台的错误码只写在自己的实现里。
 */
public interface UnionLimitSignal {

    /**
     * 这个判断负责哪个平台。
     *
     * @return 平台码
     */
    String platform();

    /**
     * 看这一次是不是官方限流。淘宝这类接口失败时仍返回对象，不抛异常。
     *
     * @param response 官方响应，可空
     * @param error 客户端异常，可空
     * @return 限流码和说明；不是限流则空
     */
    UnionLimitHit hit(Object response, Exception error);
}
