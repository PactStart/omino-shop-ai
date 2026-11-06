package com.omino.shopai.admin.web.dto;

/**
 * 改一个版位的展示参数。轮播用间隔，金刚区用每行个数。
 *
 * @param columns 每行个数，只对金刚区生效
 * @param intervalMs 轮播间隔，只对轮播生效
 */
public record MallZonePatchDto(Integer columns, Integer intervalMs) {
}
