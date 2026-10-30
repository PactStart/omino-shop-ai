package com.omino.shopai.app.service.order;

import com.omino.shopai.app.adapter.platform.PlatformOrderSource;
import com.omino.shopai.dto.order.IncomingOrder;
import com.omino.shopai.entity.OrderSyncCursor;

import java.util.List;

/**
 * 测试用空批次。生产环境的订单来源是官方客户端。
 */
public class EmptyPlatformOrderSource implements PlatformOrderSource {

    @Override
    public List<IncomingOrder> pull(String platform, OrderSyncCursor cursor) {
        return List.of();
    }
}
