package com.omino.shopai.app.adapter.platform;

import com.omino.shopai.entity.GoodsSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
/**
 * 拼多多联盟适配。密钥只读环境变量。
 */

@Component
public class PddGoodsClient implements PlatformGoodsClient {

    private final String clientId;
    private final String clientSecret;

    public PddGoodsClient(
            @Value("${UNION_PDD_CLIENT_ID:}") String clientId,
            @Value("${UNION_PDD_CLIENT_SECRET:}") String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public String platform() {
        return "pdd";
    }
/**
 * 读取详情。
 */

    @Override
    public GoodsSnapshot detail(String platformGoodsId) {
        Upstream.call(clientId, clientSecret);
        throw new PlatformCallException();
    }
/**
 * 按条件检索。
 */

    @Override
    public List<GoodsSnapshot> search(String keyword) {
        Upstream.call(clientId, clientSecret);
        throw new PlatformCallException();
    }
}
