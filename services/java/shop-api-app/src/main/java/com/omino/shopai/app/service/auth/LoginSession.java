package com.omino.shopai.app.service.auth;
/**
 * 登录成功后交给端上的访问令牌和刷新信息。
 */

public record LoginSession(String accessToken, String userId, String inviteCode, long expiresIn) {
}
