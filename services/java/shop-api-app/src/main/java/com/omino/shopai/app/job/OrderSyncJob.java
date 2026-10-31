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
 * 按平台轮流拉增量。单个平台失败不影响其它平台。
 */

    public void run() {
        for (Platform platform : Platform.values()) {
            try {
                sync.sync(platform.code());
            } catch (RuntimeException ex) {
                // 失败只记固定文案。水位留在失败前的位置，后面的平台继续拉。
                sync.noteFailure(platform.code());
            }
        }
    }
}
