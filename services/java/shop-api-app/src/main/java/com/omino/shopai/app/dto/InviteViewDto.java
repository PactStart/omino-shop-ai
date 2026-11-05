package com.omino.shopai.app.dto;

/**
 * 邀请码和落地地址。停用账号不带地址。
 *
 * @param inviteCode 邀请码
 * @param url 落地页地址
 * @param invitedCount 已邀请人数
 */
public record InviteViewDto(String inviteCode, String url, int invitedCount) {
}
