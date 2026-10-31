package com.omino.shopai.app.service.order;

import com.omino.shopai.app.adapter.platform.IPlatformOrder;
import com.omino.shopai.dto.order.IncomingOrderDto;
import com.omino.shopai.entity.OrderSyncCursor;

import java.util.List;

/**
 * 测试用空批次。生产环境的订单来源是官方客户端。
 */
public class EmptyPlatformOrderImpl implements IPlatformOrder {

    @Override
    public List<IncomingOrderDto> pull(String platform, OrderSyncCursor cursor) {
        return List.of();
    }
}
