package com.omino.shopai.app.service.mall;

import com.omino.shopai.app.dto.MallHomeDto;

/**
 * 按当前端下发商城首页布局。
 */
public interface IMallService {

    /**
     * 读出当前端能看的轮播和金刚区。
     *
     * @param clientType 客户端类型
     * @param channel 渠道
     * @return 两块布局
     */
    MallHomeDto home(String clientType, String channel);
}
