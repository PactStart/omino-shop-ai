package com.omino.shopai.app.service.goods;

import com.omino.shopai.app.dto.ConvertedLinkDto;
import com.omino.shopai.app.dto.GoodsCardDto;
import com.omino.shopai.app.dto.GoodsDetailDto;
import com.omino.shopai.entity.GoodsSnapshot;

import java.util.List;

/**
 * 商品详情、搜索和点击后转链。密钥不出这个进程。
 */
public interface IGoodsService {

    /**
     * 读取详情。有商品号时只读快照，否则按平台商品号走缓存和联盟。
     *
     * @param userId 当前用户
     * @param goodsId 本系统商品号，可空
     * @param platform 平台码，没有商品号时必填
     * @param platformGoodsId 平台商品号，没有商品号时必填
     * @return 商品详情
     */
    GoodsDetailDto detail(String userId, String goodsId, String platform, String platformGoodsId);

    /**
     * 在一个平台上按关键词搜品。没有推广身份则不出站。
     *
     * @param userId 当前用户
     * @param platform 平台码
     * @param keyword 关键词
     * @return 商品卡
     */
    List<GoodsCardDto> search(String userId, String platform, String keyword);

    /**
     * 校验授权和推广身份后生成购买链接，成功才记点击。
     *
     * @param userId 当前用户
     * @param goodsId 本系统商品号，可空
     * @param platform 平台码，没有商品号时必填
     * @param platformGoodsId 平台商品号，没有商品号时必填
     * @param preferApp 是否优先打开已安装的电商 APP；空按优先
     * @return 购买链接
     */
    ConvertedLinkDto convert(
            String userId,
            String goodsId,
            String platform,
            String platformGoodsId,
            Boolean preferApp);

    /**
     * 已授权之后才向官方要购买链接。未安装对应 APP 时不下发 deeplink。
     *
     * @param goods 要转链的商品
     * @param openInApp 是否下发 deeplink
     * @param userId 当前用户
     * @param promotionId 推广身份
     * @return 购买链接
     */
    ConvertedLinkDto issue(GoodsSnapshot goods, boolean openInApp, String userId, String promotionId);
}
