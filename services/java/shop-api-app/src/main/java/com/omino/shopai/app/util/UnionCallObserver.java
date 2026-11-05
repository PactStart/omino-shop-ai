package com.omino.shopai.app.util;

/**
 * 联盟 SDK 一次出站前后的观察点。没绑定时什么都不做。
 */
public interface UnionCallObserver {

    /**
     * 真正打官方之前。配额不够时抛出，调用方不再出站。
     *
     * @param platform 平台码
     * @param api 本系统给这个接口起的名字
     */
    default void before(String platform, String api) {
    }

    /**
     * 官方已经返回，或客户端抛了异常。计数和限流识别放在这里。
     *
     * @param platform 平台码
     * @param api 本系统给这个接口起的名字
     * @param response 官方响应；抛异常时为空
     * @param error 客户端异常；正常返回时为空
     */
    default void after(String platform, String api, Object response, Exception error) {
    }
}
