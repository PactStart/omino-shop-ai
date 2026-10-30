package com.omino.shopai.app.service.goods;

import java.util.Optional;

/**
 * 当前用户在某个平台上的推广身份。先缓存，再数据库。
 */
@FunctionalInterface
public interface UnionRelation {

    Optional<String> find(String userId, String platform);
}
