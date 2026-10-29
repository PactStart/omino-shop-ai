package com.omino.shopai.app.adapter.platform;

import com.omino.shopai.entity.GoodsSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
/**
 * 京东联盟适配。密钥只读环境变量。
 */

@Component
public class JdGoodsClient implements PlatformGoodsClient {

    private final String appKey;
    private final String appSecret;
    private final String siteId;

    public JdGoodsClient(
            @Value("${UNION_JD_APP_KEY:}") String appKey,
            @Value("${UNION_JD_APP_SECRET:}") String appSecret,
            @Value("${UNION_JD_SITE_ID:}") String siteId) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.siteId = siteId;
    }

    @Override
    public String platform() {
        return "jd";
    }
/**
 * 读取详情。
 */

    @Override
    public GoodsSnapshot detail(String platformGoodsId) {
        Upstream.call(appKey, appSecret, siteId);
        throw new PlatformCallException();
    }
/**
 * 按条件检索。
 */

    @Override
    public List<GoodsSnapshot> search(String keyword) {
        Upstream.call(appKey, appSecret, siteId);
        throw new PlatformCallException();
    }
}
