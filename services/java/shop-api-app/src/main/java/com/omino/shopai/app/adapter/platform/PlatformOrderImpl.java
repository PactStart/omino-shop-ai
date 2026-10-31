package com.omino.shopai.app.adapter.platform;

import com.omino.shopai.exception.PlatformCallException;

import com.omino.shopai.dto.order.IncomingOrderDto;
import com.omino.shopai.entity.OrderSyncCursor;
import com.omino.shopai.enums.Platform;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 四个平台的订单都从这里进同步。密钥检查在各客户端里，缺密钥不会创建官方客户端。
 */
@Component
public class PlatformOrderImpl implements IPlatformOrder {

    private final List<IPlatformGoods> clients;

    public PlatformOrderImpl(List<IPlatformGoods> clients) {
        this.clients = clients;
    }

    @Override
    public List<IncomingOrderDto> pull(String platform, OrderSyncCursor cursor) {
        Platform parsed;
        try {
            parsed = Platform.fromCode(platform);
        } catch (IllegalArgumentException ex) {
            throw new PlatformCallException();
        }
        for (IPlatformGoods client : clients) {
            if (parsed != null && client.platform() == parsed) {
                return client.pullOrders(cursor);
            }
        }
        throw new PlatformCallException();
    }
}
