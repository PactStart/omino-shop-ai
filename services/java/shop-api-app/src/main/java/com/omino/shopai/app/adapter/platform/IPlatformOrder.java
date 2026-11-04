package com.omino.shopai.app.adapter.platform;

import com.omino.shopai.app.util.UnionMaps;
import com.omino.shopai.dto.order.IncomingOrderDto;
import com.omino.shopai.entity.OrderSyncCursor;

import java.util.List;

/**
 * 某个平台的增量订单来源。
 */
public interface IPlatformOrder {

    /**
     * 只拉给出的这两段。某一段为空表示这一列已经追上，不再请求。
     *
     * @param platform 平台码
     * @param cursor 当前游标
     * @param created 下单时间这一段
     * @param updated 更新时间这一段
     * @return 这两段里的订单
     */
    List<IncomingOrderDto> pull(String platform, OrderSyncCursor cursor, UnionMaps.Window created, UnionMaps.Window updated);
}
