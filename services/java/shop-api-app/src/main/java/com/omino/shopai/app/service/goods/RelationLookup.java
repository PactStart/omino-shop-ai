package com.omino.shopai.app.service.goods;

import java.util.Optional;

/**
 * 从授权表读取 relation_id。空白视为没有。
 */
@FunctionalInterface
public interface RelationLookup {

    Optional<String> relationId(String userId, String platform);
}
