package com.omino.shopai.app.service.goods;

import com.omino.shopai.app.dto.ConvertedLinkDto;

import com.omino.shopai.exception.PlatformCallException;
import com.omino.shopai.app.adapter.platform.IPlatformGoods;
import com.omino.shopai.app.dto.PromotionLinkDto;
import com.omino.shopai.entity.GoodsSnapshot;
import com.omino.shopai.enums.Platform;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 授权通过之后才向官方要购买链接。未安装对应 APP 时不下发 deeplink。
 */
@Component
public class LinkIssuerImpl implements ILinkIssuer {

    private final List<IPlatformGoods> clients;

    public LinkIssuerImpl(List<IPlatformGoods> clients) {
        this.clients = clients;
    }

    @Override
    public ConvertedLinkDto issue(GoodsSnapshot goods, boolean openInApp, String userId, String promotionId) {
        Platform platform = goods.getPlatform();
        IPlatformGoods client = null;
        for (IPlatformGoods candidate : clients) {
            if (platform != null && platform == candidate.platform()) {
                client = candidate;
                break;
            }
        }
        if (client == null) {
            throw new PlatformCallException();
        }
        PromotionLinkDto link = client.convert(goods, openInApp, userId, promotionId);
        String deeplink = openInApp ? link.deeplink() : null;
        String expiresAt = OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).plusHours(1).toString();
        return new ConvertedLinkDto(
                goods.getId(),
                platform == null ? null : platform.code(),
                link.clickUrl(),
                deeplink,
                link.universalLink(),
                link.fallbackHttpUrl(),
                expiresAt);
    }
}
