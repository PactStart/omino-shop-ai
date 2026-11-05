package com.omino.shopai.app.goods;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.app.adapter.platform.IPlatformGoods;
import com.omino.shopai.app.dto.ConvertedLinkDto;
import com.omino.shopai.app.service.goods.GoodsServiceImpl;
import com.omino.shopai.entity.GoodsSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * 用内存商品替换转链里的表访问。签发可以换成测试桩，否则走真实实现。
 */
public final class MemoryGoodsConvert extends GoodsServiceImpl {

    private final MemoryGoods goods;
    private final Stub stub;

    /**
     * 绑定同一份内存商品，签发走真实适配器。
     *
     * @param goods 内存商品
     * @param clients 各平台适配器
     */
    public MemoryGoodsConvert(MemoryGoods goods, List<IPlatformGoods> clients) {
        this(goods, clients, null);
    }

    /**
     * 绑定同一份内存商品。给出桩时不再向适配器要链接。
     *
     * @param goods 内存商品
     * @param clients 各平台适配器
     * @param stub 替换签发；空则走真实实现
     */
    public MemoryGoodsConvert(MemoryGoods goods, List<IPlatformGoods> clients, Stub stub) {
        super(null, clients, goods, null, new ObjectMapper(), null, null, null, null);
        this.goods = goods;
        this.stub = stub;
    }

    /**
     * 测试桩给出购买链接，否则按平台适配器签发。
     *
     * @param snapshot 要转链的商品
     * @param openInApp 是否下发 deeplink
     * @param userId 当前用户
     * @param promotionId 推广身份
     * @return 购买链接
     */
    @Override
    public ConvertedLinkDto issue(GoodsSnapshot snapshot, boolean openInApp, String userId, String promotionId) {
        if (stub != null) {
            return stub.issue(snapshot, openInApp, userId, promotionId);
        }
        return super.issue(snapshot, openInApp, userId, promotionId);
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

    /**
     * 测试里替换向联盟要链接这一步。
     */
    @FunctionalInterface
    public interface Stub {

        /**
         * 直接给出购买链接。
         *
         * @param goods 商品
         * @param openInApp 是否下发 deeplink
         * @param userId 当前用户
         * @param promotionId 推广身份
         * @return 购买链接
         */
        ConvertedLinkDto issue(GoodsSnapshot goods, boolean openInApp, String userId, String promotionId);
    }
}
