package com.omino.shopai.app.service.order;

import com.omino.shopai.entity.OrderSyncCursor;
import org.springframework.stereotype.Component;

import java.util.List;
/**
 * 没有联盟密钥时拉到空批次，游标仍可前进。
 */

@Component
public class EmptyPlatformOrderSource implements PlatformOrderSource {

    @Override
    public List<IncomingOrder> pull(String platform, OrderSyncCursor cursor) {
        return List.of();
    }
}
