package com.omino.shopai.app.dto;

import com.omino.shopai.entity.KbArticle;

import java.util.List;

/**
 * 知识库命中的一条。没有命中时调用方显式说没找到。
 *
 * @param hit 是否命中
 * @param articles 命中的文章
 */
public record KbHitDto(boolean hit, List<KbArticle> articles) {

    /**
     * 明确表示没有命中。
     *
     * @return 未命中，文章列表为空
     */
    public static KbHitDto miss() {
        return new KbHitDto(false, List.of());
    }
}
