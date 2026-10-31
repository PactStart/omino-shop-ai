package com.omino.shopai.app.service.kb;

import com.omino.shopai.enums.PublishStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.entity.KbArticle;
import com.omino.shopai.mapper.KbArticleMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
/**
 * 持久化边界。事务和状态判断由调用方负责。
 */

@Repository
public class KbImpl implements IKb {

    private final KbArticleMapper articles;

    public KbImpl(KbArticleMapper articles) {
        this.articles = articles;
    }

    @Override
    public List<KbArticle> published() {
        return articles.selectList(new LambdaQueryWrapper<KbArticle>().eq(KbArticle::getStatus, PublishStatus.PUBLISHED));
    }
}
