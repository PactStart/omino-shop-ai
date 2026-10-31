package com.omino.shopai.admin.service.kb;

import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.KbArticle;
import com.omino.shopai.enums.PublishStatus;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.KbArticleMapper;
import com.omino.shopai.support.Ids;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 知识库文章。新建即发布。下架只改状态，不删除。
 */
@Service
public class AdminKbService {

    private final KbArticleMapper articles;

    /**
     * 注入文章表。
     *
     * @param articles 知识库
     */
    public AdminKbService(KbArticleMapper articles) {
        this.articles = articles;
    }

    /**
     * 全部文章，包含已下架。
     *
     * @return 文章列表
     */
    public List<KbArticle> list() {
        return articles.selectList(null);
    }

    /**
     * 新建一篇已发布的文章。
     *
     * @param title 标题
     * @param body 正文
     * @return 新文章
     */
    public KbArticle create(String title, String body) {
        KbArticle article = new KbArticle();
        article.setId(Ids.ulid());
        article.setTitle(requireText(title, "标题不能为空"));
        article.setBody(requireText(body, "正文不能为空"));
        article.setStatus(PublishStatus.PUBLISHED);
        article.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        articles.insert(article);
        return article;
    }

    /**
     * 修改标题、正文或上下架。状态只接受 published 或 retired。
     *
     * @param id 文章主键
     * @param title 新标题，可空
     * @param body 新正文，可空
     * @param status 新状态码，可空
     * @return 更新后的文章
     */
    public KbArticle patch(String id, String title, String body, String status) {
        KbArticle article = articles.selectById(id);
        if (article == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "文章不存在");
        }
        if (title != null) {
            article.setTitle(requireText(title, "标题不能为空"));
        }
        if (body != null) {
            article.setBody(requireText(body, "正文不能为空"));
        }
        if (status != null && !status.isBlank()) {
            PublishStatus next = PublishStatus.fromCode(status.trim());
            if (next != PublishStatus.PUBLISHED && next != PublishStatus.RETIRED) {
                throw new ApiBusinessException(ErrorCodes.VALIDATION, "状态只接受 published 或 retired");
            }
            article.setStatus(next);
        }
        article.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        articles.updateById(article);
        return article;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, message);
        }
        return value.trim();
    }
}
