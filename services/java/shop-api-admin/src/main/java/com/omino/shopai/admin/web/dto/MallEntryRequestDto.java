package com.omino.shopai.admin.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 新建或修改一条商城条目。
 *
 * @param zone 版位码
 * @param categoryId 分类主键，轮播必须为空
 * @param title 标题
 * @param imageObjectKey 图片地址或对象键
 * @param sort 排序
 * @param status 状态码
 * @param startsAt 生效开始，可空
 * @param endsAt 生效结束，可空
 * @param clientTypes 端，空表示全端
 * @param channels 渠道，空表示全渠道
 * @param actionType 点击类型
 * @param action 点击参数
 */
public record MallEntryRequestDto(
        String zone,
        String categoryId,
        String title,
        String imageObjectKey,
        Integer sort,
        String status,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        List<String> clientTypes,
        List<String> channels,
        String actionType,
        JsonNode action) {
}
