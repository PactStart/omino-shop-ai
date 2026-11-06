package com.omino.shopai.app.adapter.platform;

import com.omino.shopai.app.dto.GoodsHitPage;
import com.omino.shopai.app.dto.GoodsSearchRequestDto;
import com.omino.shopai.app.dto.ParsedMaterial;
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

    /**
     * 分页搜品。默认只取原来的第一页，总数未知时为 0。筛选和游标由实现类按平台能支持的字段使用。
     *
     * @param query 搜索条件
     * @param userId 当前用户
     * @param promotionId 推广身份
     * @return 一页快照
     */
    default GoodsHitPage searchPage(GoodsSearchRequestDto query, String userId, String promotionId) {
        String keyword = query == null ? null : query.keyword();
        return new GoodsHitPage(search(keyword, userId, promotionId), 0);
    }

    /**
     * 这个平台能不能生成口令。
     *
     * @return 能生成时为真
     */
    default boolean offersCommand() {
        return false;
    }

    /**
     * 生成商品口令。不支持的平台返回空，不出站。
     *
     * @param goods 商品
     * @param userId 当前用户
     * @param promotionId 推广身份
     * @return 口令；没有则为空
     */
    default String command(GoodsSnapshot goods, String userId, String promotionId) {
        return null;
    }

    /**
     * 这个平台能不能做万能解析。
     *
     * @return 能解析时为真
     */
    default boolean offersParse() {
        return false;
    }

    /**
     * 解析一段链接或口令。不支持的平台返回空，不出站。
     *
     * @param content 用户贴来的内容
     * @param userId 当前用户
     * @param promotionId 推广身份
     * @return 解析结果；没有则为空
     */
    default ParsedMaterial parse(String content, String userId, String promotionId) {
        return null;
    }

    /**
     * 这个平台能不能查相似商品。
     *
     * @return 能查时为真
     */
    default boolean offersSimilar() {
        return false;
    }

    /**
     * 查相似商品。不支持的平台返回空页，不出站。
     *
     * @param platformGoodsId 平台商品号
     * @param page 页码，从 1 起
     * @param size 每页条数
     * @param userId 当前用户
     * @param promotionId 推广身份
     * @return 一页快照
     */
    default GoodsHitPage similar(String platformGoodsId, int page, int size, String userId, String promotionId) {
        return GoodsHitPage.empty();
    }

    /**
     * 这个平台能不能出猜你喜欢。
     *
     * @return 能出时为真
     */
    default boolean offersGuess() {
        return false;
    }

    /**
     * 猜你喜欢。不支持的平台返回空页，不出站。
     *
     * @param page 页码，从 1 起
     * @param size 每页条数
     * @param userId 当前用户
     * @param promotionId 推广身份
     * @return 一页快照
     */
    default GoodsHitPage guess(int page, int size, String userId, String promotionId) {
        return GoodsHitPage.empty();
    }
}
