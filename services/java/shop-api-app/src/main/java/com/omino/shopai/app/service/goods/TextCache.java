package com.omino.shopai.app.service.goods;

import java.time.Duration;

/**
 * 短字符串缓存。推广身份先读这里，没有再查授权表。
 */
public interface TextCache {

    String get(String key);

    void put(String key, String value, Duration ttl);
}
