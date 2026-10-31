package com.omino.shopai.app.kb;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omino.shopai.app.service.kb.KbImpl;
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
        List<KbArticle> found = new KbImpl(articles).published();
        assertTrue(found.stream().noneMatch(article -> article.getStatus() == PublishStatus.RETIRED));
    }
}
