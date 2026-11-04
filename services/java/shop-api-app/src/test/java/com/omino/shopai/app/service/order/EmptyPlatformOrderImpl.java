package com.omino.shopai.app.service.order;

import com.omino.shopai.app.adapter.platform.IPlatformOrder;
import com.omino.shopai.app.util.UnionMaps;
import com.omino.shopai.dto.order.IncomingOrderDto;
import com.omino.shopai.entity.OrderSyncCursor;

import java.util.List;

/**
 * 测试用空批次。生产环境的订单来源是官方客户端。
 */
public class EmptyPlatformOrderImpl implements IPlatformOrder {

    /**
     * 空批次，两段都不出订单。
     *
     * @param platform 平台码
     * @param cursor 当前游标
     * @param created 下单时间这一段
     * @param updated 更新时间这一段
     * @return 空列表
     */
    @Override
    public List<IncomingOrderDto> pull(
            String platform, OrderSyncCursor cursor, UnionMaps.Window created, UnionMaps.Window updated) {
        return List.of();
    }
}
