package com.omino.shopai.app.dto;

import java.util.List;

/**
 * 一组关键词。联想词和热搜词共用。
 *
 * @param list 词
 */
public record KeywordListDto(List<String> list) {
}
