package com.omino.shopai.app.dto;

import com.omino.shopai.entity.GoodsSnapshot;

import java.util.List;

/**
 * 联盟返回的一页商品。还没写成对外的商品卡。
 *
 * @param list 当前页快照
 * @param total 平台给的总数；平台没给时用本页条数
 * @param nextId 下一页游标；平台不使用游标时为空
 */
public record GoodsHitPage(List<GoodsSnapshot> list, long total, String nextId) {

    /**
     * 没有翻页游标的一页。
     *
     * @param list 当前页快照
     * @param total 平台给的总数
     */
    public GoodsHitPage(List<GoodsSnapshot> list, long total) {
        this(list, total, null);
    }

    /**
     * 没有结果的一页。
     *
     * @return 空列表，总数 0
     */
    public static GoodsHitPage empty() {
        return new GoodsHitPage(List.of(), 0, null);
    }
}
