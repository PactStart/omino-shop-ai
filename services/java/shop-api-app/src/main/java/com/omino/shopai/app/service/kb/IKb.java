package com.omino.shopai.app.service.kb;

import com.omino.shopai.entity.KbArticle;

import java.util.List;
/**
 * 知识库检索的存储端口。
 */

public interface IKb {

    List<KbArticle> published();
}
