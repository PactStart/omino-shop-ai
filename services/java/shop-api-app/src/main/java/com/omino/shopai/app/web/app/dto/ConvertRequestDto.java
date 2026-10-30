package com.omino.shopai.app.web.app.dto;

/**
 * 转链目标。未授权时由服务返回 40301。
 */
public record ConvertRequestDto(String goodsId, String platform, String platformGoodsId, Boolean preferApp) {
}
