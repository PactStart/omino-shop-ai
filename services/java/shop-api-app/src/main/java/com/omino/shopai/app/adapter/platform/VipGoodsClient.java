package com.omino.shopai.app.adapter.platform;

import com.omino.shopai.entity.GoodsSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
/**
 * 唯品会联盟适配。密钥只读环境变量。
 */

@Component
public class VipGoodsClient implements PlatformGoodsClient {

    private final String appKey;
    private final String appSecret;

    public VipGoodsClient(
            @Value("${UNION_VIP_APP_KEY:}") String appKey,
            @Value("${UNION_VIP_APP_SECRET:}") String appSecret) {
        this.appKey = appKey;
        this.appSecret = appSecret;
    }

    @Override
    public String platform() {
        return "vip";
    }
/**
 * 读取详情。
 */

    @Override
    public GoodsSnapshot detail(String platformGoodsId) {
        Upstream.call(appKey, appSecret);
        throw new PlatformCallException();
    }
/**
 * 按条件检索。
 */

    @Override
    public List<GoodsSnapshot> search(String keyword) {
        Upstream.call(appKey, appSecret);
        throw new PlatformCallException();
    }
}
