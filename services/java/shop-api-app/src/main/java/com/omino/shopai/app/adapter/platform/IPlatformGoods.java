package com.omino.shopai.app.adapter.platform;

import com.omino.shopai.app.dto.PromotionLinkDto;
import com.omino.shopai.app.util.UnionMaps;

import com.omino.shopai.dto.order.IncomingOrderDto;
import com.omino.shopai.entity.GoodsSnapshot;
import com.omino.shopai.entity.OrderSyncCursor;
import com.omino.shopai.enums.Platform;

import java.util.List;

/**
 * 各平台搜品、转链和订单的端口。官方调用只发生在实现类里。
 */
public interface IPlatformGoods {

    /**
     * 这个实现负责哪个平台。
     *
     * @return 平台枚举
     */
    Platform platform();

    /**
     * 按平台商品号取详情。
     *
     * @param platformGoodsId 平台商品号
     * @param userId 当前用户
     * @param promotionId 推广身份
     * @return 快照；官方没有该商品时为空
     */
    GoodsSnapshot detail(String platformGoodsId, String userId, String promotionId);

    /**
     * 按关键词搜品。
     *
     * @param keyword 关键词
     * @param userId 当前用户
     * @param promotionId 推广身份
     * @return 快照列表，可空列表
     */
    List<GoodsSnapshot> search(String keyword, String userId, String promotionId);

    /**
     * 已授权之后才由签发器调用。响应里不放推广位。
     *
     * @param goods 要转链的商品
     * @param openInApp 是否下发 deeplink
     * @param userId 当前用户
     * @param promotionId 推广身份
     * @return 购买链接
     */
    PromotionLinkDto convert(GoodsSnapshot goods, boolean openInApp, String userId, String promotionId);

    /**
     * 只拉给出的这两段。官方空列表是成功的空批次，失败则抛出且不前进游标。
     *
     * @param cursor 该平台的水位
     * @param created 下单时间这一段；空表示跳过
     * @param updated 更新时间这一段；空表示跳过
     * @return 这两段里的订单
     */
    List<IncomingOrderDto> pullOrders(OrderSyncCursor cursor, UnionMaps.Window created, UnionMaps.Window updated);
}
