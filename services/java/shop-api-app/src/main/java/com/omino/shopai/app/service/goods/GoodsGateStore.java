package com.omino.shopai.app.service.goods;
/**
 * 授权、安装和点击的存储端口。
 */

public interface GoodsGateStore {

    boolean authorized(String userId, String platform);

    boolean installed(String userId, String platform);

    void click(String userId, String platform, String goodsId, String platformGoodsId);

    int clicks(String userId);
}
