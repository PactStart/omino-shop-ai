package com.omino.shopai.app.service.kb;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.app.dto.KbHitDto;
import com.omino.shopai.entity.KbArticle;
import com.omino.shopai.enums.PublishStatus;
import com.omino.shopai.mapper.KbArticleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 按问题检索已发布的知识库。未命中时明确告诉调用方没找到。
 */
@Service
public class KbSearchServiceImpl implements IKbSearchService {

    private final KbArticleMapper articles;

    /**
     * 注入知识库文章表。检索只读已发布的行。
     *
     * @param articles 知识库文章表
     */
    public KbSearchServiceImpl(KbArticleMapper articles) {
        this.articles = articles;
    }

    /**
     * 按标题和正文检索已发布文章，最多五条。空白问题直接未命中。
     *
     * @param query 检索词
     * @return 命中的文章；没有命中时 hit 为假
     */
    @Override
    public KbHitDto search(String query) {
        if (query == null || query.isBlank()) {
            return KbHitDto.miss();
        }
        String needle = query.trim();
        List<KbArticle> hits = published().stream()
                .filter(article -> contains(article.getTitle(), needle) || contains(article.getBody(), needle))
                .limit(5)
                .toList();
        if (hits.isEmpty()) {
            return KbHitDto.miss();
        }
        return new KbHitDto(true, hits);
    }

    /**
     * 读出已发布的知识库文章。下架的不在这里出现。
     *
     * @return 已发布的文章
     */
    protected List<KbArticle> published() {
        return articles.selectList(new LambdaQueryWrapper<KbArticle>().eq(KbArticle::getStatus, PublishStatus.PUBLISHED));
    }

    /**
     * 正文或标题是否包含这段检索词。空文本不算命中。
     *
     * @param text 被检索的文本，可空
     * @param needle 检索词
     * @return 文本包含检索词时为真
     */
    private static boolean contains(String text, String needle) {
        return text != null && text.contains(needle);
    }
}
