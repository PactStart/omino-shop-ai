package com.omino.shopai.app.adapter.platform;

import com.omino.shopai.dto.order.IncomingOrder;
import com.omino.shopai.entity.OrderSyncCursor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 四个平台的订单都从这里进同步。密钥检查在各客户端里，缺密钥不会创建官方客户端。
 */
@Component
public class SdkPlatformOrderSource implements PlatformOrderSource {

    private final List<PlatformGoodsClient> clients;

    public SdkPlatformOrderSource(List<PlatformGoodsClient> clients) {
        this.clients = clients;
    }

    @Override
    public List<IncomingOrder> pull(String platform, OrderSyncCursor cursor) {
        for (PlatformGoodsClient client : clients) {
            if (client.platform().equals(platform)) {
                return client.pullOrders(cursor);
            }
        }
        throw new PlatformCallException();
    }
}
