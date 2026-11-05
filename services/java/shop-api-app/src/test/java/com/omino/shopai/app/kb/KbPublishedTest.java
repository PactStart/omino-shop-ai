package com.omino.shopai.app.kb;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omino.shopai.app.service.kb.KbSearchServiceImpl;
import com.omino.shopai.entity.KbArticle;
import com.omino.shopai.enums.PublishStatus;
import com.omino.shopai.mapper.KbArticleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KbPublishedTest {

    @Test
    void retiredArticleIsNotReturned() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), KbArticle.class);
        KbArticleMapper articles = mock(KbArticleMapper.class);
        KbArticle retired = new KbArticle();
        retired.setStatus(PublishStatus.RETIRED);
        when(articles.selectList(any())).thenAnswer(invocation -> {
            Object argument = invocation.getArgument(0);
            boolean publishedOnly = false;
            if (argument instanceof com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> wrapper) {
                String sql = String.valueOf(wrapper.getSqlSegment());
                publishedOnly = wrapper.getParamNameValuePairs().containsValue(PublishStatus.PUBLISHED)
                        || sql.toLowerCase().contains("published");
            }
            if (publishedOnly) {
                return List.of();
            }
            return List.of(retired);
        });
        List<KbArticle> found = new PublishedProbe(articles).published();
        assertTrue(found.stream().noneMatch(article -> article.getStatus() == PublishStatus.RETIRED));
    }

    /**
     * 把已发布文章查询暴露给测试。查询条件仍走真实实现。
     */
    private static final class PublishedProbe extends KbSearchServiceImpl {

        /**
         * 绑定被桩掉的文章表。
         *
         * @param articles 文章表
         */
        private PublishedProbe(KbArticleMapper articles) {
            super(articles);
        }

        /**
         * 读出已发布的知识库文章。
         *
         * @return 已发布的文章
         */
        @Override
        public List<KbArticle> published() {
            return super.published();
        }
    }
}
