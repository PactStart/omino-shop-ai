package com.omino.shopai.app.service.kb;

import com.omino.shopai.entity.KbArticle;
import org.springframework.stereotype.Service;

import java.util.List;
/**
 * KbSearchService 编排用例，事务和校验放在这里。
 */

@Service
public class KbSearchService {

    private final IKb book;

    public KbSearchService(IKb book) {
        this.book = book;
    }
/**
 * 按条件检索。
 */

    public KbHitDto search(String query) {
        if (query == null || query.isBlank()) {
            return KbHitDto.miss();
        }
        String needle = query.trim();
        List<KbArticle> hits = book.published().stream()
                .filter(article -> contains(article.getTitle(), needle) || contains(article.getBody(), needle))
                .limit(5)
                .toList();
        if (hits.isEmpty()) {
            return KbHitDto.miss();
        }
        return new KbHitDto(true, hits);
    }

    private static boolean contains(String text, String needle) {
        return text != null && text.contains(needle);
    }
/**
 * 知识库命中的一条。没有命中时调用方显式说没找到。
 */

    public record KbHitDto(boolean hit, List<KbArticle> articles) {
        public static KbHitDto miss() {
            return new KbHitDto(false, List.of());
        }
    }
}
