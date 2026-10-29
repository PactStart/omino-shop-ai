package com.omino.shopai.app.adapter.platform;

import com.omino.shopai.entity.GoodsSnapshot;

import java.util.List;
/**
 * 各平台搜品和转链的端口。
 */

public interface PlatformGoodsClient {

    String platform();

    GoodsSnapshot detail(String platformGoodsId);

    List<GoodsSnapshot> search(String keyword);
}
