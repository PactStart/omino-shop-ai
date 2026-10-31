package com.omino.shopai.app.dto;

/**
 * 一个平台的授权状态。已授权或不需要授权时 authUrl 为空。
 */
public record UnionAuthDto(String platform, boolean required, boolean authorized, String authUrl) {
}
