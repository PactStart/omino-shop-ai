package com.omino.shopai.app.service.goods;

import com.omino.shopai.app.dto.ConvertedLinkDto;

import com.omino.shopai.entity.GoodsSnapshot;
/**
 * 把商品收成 deeplink。密钥不出这个进程。
 */

public interface ILinkIssuer {

    ConvertedLinkDto issue(GoodsSnapshot goods, boolean openInApp, String userId, String promotionId);
}
