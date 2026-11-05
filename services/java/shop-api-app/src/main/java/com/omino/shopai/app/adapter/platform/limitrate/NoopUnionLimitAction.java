package com.omino.shopai.app.adapter.platform.limitrate;

import org.springframework.stereotype.Component;

/**
 * 限流后的动作先留空。冷却、暂停该平台同步、告警以后写在这个方法里。
 */
@Component
public class NoopUnionLimitAction implements UnionLimitAction {

    /**
     * 现在什么都不做。
     *
     * @param platform 平台码
     * @param api 本系统给这个接口起的名字
     * @param code 平台给出的限流码
     * @param message 平台给出的说明，可空
     */
    @Override
    public void onLimited(String platform, String api, String code, String message) {
    }
}
