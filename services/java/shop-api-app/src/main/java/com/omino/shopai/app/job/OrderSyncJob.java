package com.omino.shopai.app.job;

import com.omino.shopai.app.service.order.OrderSyncService;
import org.springframework.stereotype.Component;

import java.util.List;
/**
 * 定时按平台拉订单增量。
 */

@Component
public class OrderSyncJob {

    private static final List<String> PLATFORMS = List.of("taobao", "jd", "pdd", "vip");

    private final OrderSyncService sync;

    public OrderSyncJob(OrderSyncService sync) {
        this.sync = sync;
    }
/**
 * 按平台轮流拉增量。单个平台失败不影响其它平台。
 */

    public void run() {
        for (String platform : PLATFORMS) {
            sync.sync(platform);
        }
    }
}
