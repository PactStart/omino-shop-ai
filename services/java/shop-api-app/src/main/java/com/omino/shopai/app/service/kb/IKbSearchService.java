package com.omino.shopai.app.service.kb;

import com.omino.shopai.app.dto.KbHitDto;

/**
 * 按问题检索已发布的知识库。未命中时明确告诉调用方没找到。
 */
public interface IKbSearchService {

    /**
     * 按标题和正文检索已发布文章，最多五条。空白问题直接未命中。
     *
     * @param query 检索词
     * @return 命中的文章；没有命中时 hit 为假
     */
    KbHitDto search(String query);
}
