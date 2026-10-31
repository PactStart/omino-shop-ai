package com.omino.shopai.app.adapter.platform;

import com.omino.shopai.dto.order.IncomingOrderDto;
import com.omino.shopai.entity.OrderSyncCursor;

import java.util.List;

/**
 * 某个平台的增量订单来源。
 */
public interface IPlatformOrder {

    List<IncomingOrderDto> pull(String platform, OrderSyncCursor cursor);
}
