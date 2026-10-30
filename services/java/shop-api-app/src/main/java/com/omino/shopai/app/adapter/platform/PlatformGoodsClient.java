package com.omino.shopai.app.adapter.platform;

import com.omino.shopai.dto.order.IncomingOrder;
import com.omino.shopai.entity.GoodsSnapshot;
import com.omino.shopai.entity.OrderSyncCursor;

import java.util.List;

/**
 * 各平台搜品、转链和订单的端口。官方调用只发生在实现类里。
 */
public interface PlatformGoodsClient {

    String platform();

    GoodsSnapshot detail(String platformGoodsId);

    List<GoodsSnapshot> search(String keyword);

    /** 已授权之后才由签发器调用。响应里不放推广位。 */
    PromotionLink convert(GoodsSnapshot goods, boolean openInApp, String userId);

    /** 按游标拉增量。官方空列表是成功的空批次，失败则抛出且不前进游标。 */
    List<IncomingOrder> pullOrders(OrderSyncCursor cursor);
}
