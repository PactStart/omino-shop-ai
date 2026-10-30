package com.omino.shopai.app.dto;

import java.util.List;

/**
 * 一页列表。商品、订单、提现共用这一个形状。
 *
 * @param list 当前页
 * @param page 页码，从 1 起
 * @param size 每页条数
 * @param total 总条数
 * @param <T> 行的类型
 */
public record PageDto<T>(List<T> list, int page, int size, long total) {
}
