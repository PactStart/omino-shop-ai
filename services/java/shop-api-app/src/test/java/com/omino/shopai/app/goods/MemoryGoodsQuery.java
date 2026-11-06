package com.omino.shopai.app.goods;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.app.adapter.dataoke.DataokeClient;
import com.omino.shopai.app.adapter.platform.IPlatformGoods;
import com.omino.shopai.app.service.goods.GoodsServiceImpl;
import com.omino.shopai.entity.GoodsSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * 用内存商品替换查询里的表访问，搜索和详情仍走真实实现。
 */
public final class MemoryGoodsQuery extends GoodsServiceImpl {

    private final MemoryGoods goods;

    /**
     * 绑定同一份内存商品。表访问全部转给它，详情短缓存也用它。
     *
     * @param goods 内存商品
     * @param clients 各平台适配器
     * @param json 缓存序列化
     */
    public MemoryGoodsQuery(MemoryGoods goods, List<IPlatformGoods> clients, ObjectMapper json) {
        super(null, clients, goods, null, json, null, null, null, null, new DataokeClient("", ""));
        this.goods = goods;
    }

    /**
     * 从内存商品按主键读取。
     *
     * @param id 商品号
     * @return 快照；没有则空
     */
    @Override
    protected Optional<GoodsSnapshot> findById(String id) {
        return goods.findById(id);
    }

    /**
     * 从内存商品按平台商品号读取。
     *
     * @param platform 平台码
     * @param platformGoodsId 平台商品号
     * @return 快照；没有则空
     */
    @Override
    protected Optional<GoodsSnapshot> findByPlatform(String platform, String platformGoodsId) {
        return goods.findByPlatform(platform, platformGoodsId);
    }

    /**
     * 把快照放进内存商品。
     *
     * @param snapshot 待保存的快照
     * @return 保存后的快照
     */
    @Override
    protected GoodsSnapshot save(GoodsSnapshot snapshot) {
        return goods.save(snapshot);
    }

    /**
     * 在内存商品里记一次浏览。
     *
     * @param userId 当前用户
     * @param goodsId 商品号
     * @param platform 平台码
     * @param platformGoodsId 平台商品号
     * @param title 标题
     */
    @Override
    protected void browse(String userId, String goodsId, String platform, String platformGoodsId, String title) {
        goods.browse(userId, goodsId, platform, platformGoodsId, title);
    }

    /**
     * 从内存商品读取推广身份。
     *
     * @param userId 当前用户
     * @param platform 平台码
     * @return 推广身份；没有则空
     */
    @Override
    protected Optional<String> relationId(String userId, String platform) {
        return goods.relationId(userId, platform);
    }

    /**
     * 看内存商品里的授权状态。
     *
     * @param userId 当前用户
     * @param platform 平台码
     * @return 已授权时为真
     */
    @Override
    protected boolean authorized(String userId, String platform) {
        return goods.authorized(userId, platform);
    }

    /**
     * 看内存商品里是否报过已安装。
     *
     * @param userId 当前用户
     * @param platform 平台码
     * @return 已安装时为真
     */
    @Override
    protected boolean installed(String userId, String platform) {
        return goods.installed(userId, platform);
    }

    /**
     * 在内存商品里记一次点击。
     *
     * @param userId 当前用户
     * @param platform 平台码
     * @param goodsId 商品号
     * @param platformGoodsId 平台商品号
     */
    @Override
    protected void click(String userId, String platform, String goodsId, String platformGoodsId) {
        goods.click(userId, platform, goodsId, platformGoodsId);
    }
}
