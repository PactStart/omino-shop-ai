package com.omino.shopai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 一页列表。商品、订单、提现和后台列表共用这一个形状。
 *
 * @param list 当前页
 * @param page 页码，从 1 起
 * @param size 每页条数
 * @param total 总条数
 * @param nextId 下一页游标；只有商品搜索会写，没有时不出现在 JSON 里
 * @param <T> 行的类型
 */
public record PageDto<T>(
        List<T> list,
        int page,
        int size,
        long total,
        @JsonInclude(JsonInclude.Include.NON_NULL) String nextId) {

    /**
     * 没有翻页游标的一页。
     *
     * @param list 当前页
     * @param page 页码，从 1 起
     * @param size 每页条数
     * @param total 总条数
     */
    public PageDto(List<T> list, int page, int size, long total) {
        this(list, page, size, total, null);
    }
}
