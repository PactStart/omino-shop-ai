package com.omino.shopai.app.service.goods;

import java.util.Optional;

/**
 * 授权、安装和点击的存储端口。
 */

public interface IGoodsGate {

    boolean authorized(String userId, String platform);

    /** 该用户在这个平台上的 relation_id。没有或空白则空。 */
    Optional<String> relationId(String userId, String platform);

    boolean installed(String userId, String platform);

    void click(String userId, String platform, String goodsId, String platformGoodsId);

    int clicks(String userId);
}
