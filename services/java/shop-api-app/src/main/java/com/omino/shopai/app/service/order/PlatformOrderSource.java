package com.omino.shopai.app.service.order;

import com.omino.shopai.entity.OrderSyncCursor;

import java.util.List;
/**
 * 某个平台的增量订单来源。
 */

public interface PlatformOrderSource {

    List<IncomingOrder> pull(String platform, OrderSyncCursor cursor);
}
