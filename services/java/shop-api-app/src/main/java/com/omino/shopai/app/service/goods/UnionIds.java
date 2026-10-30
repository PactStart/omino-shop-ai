package com.omino.shopai.app.service.goods;

/**
 * 出站时该读哪一条授权。拼多多的 pid 用该用户京东那条 relation_id。
 */
public final class UnionIds {

    private UnionIds() {
    }

    public static String source(String platform) {
        return "pdd".equals(platform) ? "jd" : platform;
    }
}
