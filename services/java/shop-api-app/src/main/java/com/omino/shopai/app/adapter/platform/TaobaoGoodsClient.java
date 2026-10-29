package com.omino.shopai.app.adapter.platform;

import com.omino.shopai.entity.GoodsSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
/**
 * 淘宝联盟适配。密钥只读环境变量。
 */

@Component
public class TaobaoGoodsClient implements PlatformGoodsClient {

    private final String appKey;
    private final String appSecret;
    private final String pid;

    public TaobaoGoodsClient(
            @Value("${UNION_TAOBAO_APP_KEY:}") String appKey,
            @Value("${UNION_TAOBAO_APP_SECRET:}") String appSecret,
            @Value("${UNION_TAOBAO_PID:}") String pid) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.pid = pid;
    }

    @Override
    public String platform() {
        return "taobao";
    }
/**
 * 读取详情。
 */

    @Override
    public GoodsSnapshot detail(String platformGoodsId) {
        Upstream.call(appKey, appSecret, pid);
        throw new PlatformCallException();
    }
/**
 * 按条件检索。
 */

    @Override
    public List<GoodsSnapshot> search(String keyword) {
        Upstream.call(appKey, appSecret, pid);
        throw new PlatformCallException();
    }
}
