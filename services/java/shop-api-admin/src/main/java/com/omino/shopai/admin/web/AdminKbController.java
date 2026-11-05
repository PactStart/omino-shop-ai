package com.omino.shopai.admin.web;

import com.omino.shopai.admin.service.kb.AdminKbService;
import com.omino.shopai.admin.web.dto.KbArticleDto;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.entity.KbArticle;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库文章。没有删除。
 */
@RestController
@Tag(name = "知识库")
@RequestMapping("/v1/kb/articles")
public class AdminKbController {

    private final AdminKbService articles;

    /**
     * 注入知识库用例。
     *
     * @param articles 文章服务
     */
    public AdminKbController(AdminKbService articles) {
        this.articles = articles;
    }

    /**
     * 列出全部文章，包含已下架。
     *
     * @return 文章列表
     */
    @Operation(summary = "列出知识库文章")
    @GetMapping
    public ApiResponse<List<KbArticle>> list() {
        return ApiResponse.ok(articles.list());
    }

    /**
     * 新建一篇已发布的文章。
     *
     * @param body 标题和正文
     * @return 新文章
     */
    @Operation(summary = "新建知识库文章")
    @PostMapping
    public ApiResponse<KbArticle> create(@RequestBody KbArticleDto body) {
        return ApiResponse.ok(articles.create(body.title(), body.body()));
    }

    /**
     * 修改标题、正文或上下架。
     *
     * @param id 文章主键
     * @param body 要改的字段
     * @return 更新后的文章
     */
    @Operation(summary = "修改知识库文章")
    @PatchMapping("/{id}")
    public ApiResponse<KbArticle> patch(@PathVariable String id, @RequestBody KbArticleDto body) {
        return ApiResponse.ok(articles.patch(id, body.title(), body.body(), body.status()));
    }
}
