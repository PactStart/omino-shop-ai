package com.omino.shopai.app.service.goods;

import com.omino.shopai.app.adapter.platform.PlatformCallException;
import com.omino.shopai.app.adapter.platform.PlatformGoodsClient;
import com.omino.shopai.app.adapter.platform.PromotionLink;
import com.omino.shopai.entity.GoodsSnapshot;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 授权通过之后才向官方要购买链接。未安装对应 APP 时不下发 deeplink。
 */
@Component
public class ServerLinkIssuer implements LinkIssuer {

    private final List<PlatformGoodsClient> clients;

    public ServerLinkIssuer(List<PlatformGoodsClient> clients) {
        this.clients = clients;
    }

    @Override
    public ConvertedLink issue(GoodsSnapshot goods, boolean openInApp, String userId, String promotionId) {
        String platform = goods.getPlatform() == null ? null : goods.getPlatform().code();
        PlatformGoodsClient client = null;
        for (PlatformGoodsClient candidate : clients) {
            if (candidate.platform().equals(platform)) {
                client = candidate;
                break;
            }
        }
        if (client == null) {
            throw new PlatformCallException();
        }
        PromotionLink link = client.convert(goods, openInApp, userId, promotionId);
        String deeplink = openInApp ? link.deeplink() : null;
        String expiresAt = OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).plusHours(1).toString();
        return new ConvertedLink(
                goods.getId(),
                platform,
                link.clickUrl(),
                deeplink,
                link.universalLink(),
                link.fallbackHttpUrl(),
                expiresAt);
    }
}
