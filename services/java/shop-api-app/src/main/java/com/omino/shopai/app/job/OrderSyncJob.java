package com.omino.shopai.app.job;

import com.omino.shopai.app.service.order.OrderSyncService;
import com.omino.shopai.enums.Platform;
import org.springframework.stereotype.Component;
/**
 * 定时按平台拉订单增量。
 */

@Component
public class OrderSyncJob {

    private final OrderSyncService sync;

    public OrderSyncJob(OrderSyncService sync) {
        this.sync = sync;
    }
    /**
     * 每个平台受理一轮追赶后就返回。单个平台失败记在自己的水位上，不挡住别的平台。
     */
    public void run() {
        for (Platform platform : Platform.values()) {
            sync.submit(platform.code());
        }
    }
}
