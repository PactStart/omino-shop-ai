package com.omino.shopai.app.service.goods;

import com.omino.shopai.entity.GoodsSnapshot;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
/**
 * 按平台拼 deeplink。关系位不出现在响应里。
 */

@Component
public class ServerLinkIssuer implements LinkIssuer {
/**
 * 签发短期凭证。
 */

    @Override
    public ConvertedLink issue(GoodsSnapshot goods, boolean openInApp) {
        String page = goods.getGoodsUrl();
        String platform = goods.getPlatform() == null ? null : goods.getPlatform().code();
        String deeplink = openInApp
                ? "openapp." + platform + "://item/" + goods.getPlatformGoodsId()
                : null;
        String expiresAt = OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).plusHours(1).toString();
        return new ConvertedLink(goods.getId(), platform, page, deeplink, page, page, expiresAt);
    }
}
