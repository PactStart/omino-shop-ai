package com.omino.shopai.app.adapter.platform;

import com.omino.shopai.app.util.UnionMaps;
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

    /**
     * 找到这个平台的客户端，只拉服务给出的两段。
     *
     * @param platform 平台码
     * @param cursor 当前游标
     * @param created 下单时间这一段
     * @param updated 更新时间这一段
     * @return 这两段里的订单
     */
    @Override
    public List<IncomingOrderDto> pull(
            String platform, OrderSyncCursor cursor, UnionMaps.Window created, UnionMaps.Window updated) {
        Platform parsed;
        try {
            parsed = Platform.fromCode(platform);
        } catch (IllegalArgumentException ex) {
            throw new PlatformCallException();
        }
        for (IPlatformGoods client : clients) {
            if (parsed != null && client.platform() == parsed) {
                return client.pullOrders(cursor, created, updated);
            }
        }
        throw new PlatformCallException();
    }
}
