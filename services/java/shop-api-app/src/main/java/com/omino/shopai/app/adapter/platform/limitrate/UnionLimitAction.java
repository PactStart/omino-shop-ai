package com.omino.shopai.app.adapter.platform.limitrate;

/**
 * 官方限流之后可以做的事。默认实现是空的。
 */
public interface UnionLimitAction {

    /**
     * 这一次调用已经被官方限流。不要在这里重试，订单同步会自己停在当前这一段。
     *
     * @param platform 平台码
     * @param api 本系统给这个接口起的名字
     * @param code 平台给出的限流码
     * @param message 平台给出的说明，可空
     */
    void onLimited(String platform, String api, String code, String message);
}
