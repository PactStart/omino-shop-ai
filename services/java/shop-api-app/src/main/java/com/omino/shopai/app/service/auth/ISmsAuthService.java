package com.omino.shopai.app.service.auth;

import com.omino.shopai.app.dto.LoginSessionDto;

/**
 * 手机号验证码登录。校验、建号和邀请绑定都在这一处完成。
 */
public interface ISmsAuthService {

    /**
     * 发验证码前校验手机号。mock 不打外部短信。
     *
     * @param phone 手机号
     * @return 无
     */
    void send(String phone);

    /**
     * 校验验证码并完成登录。新用户在同一事务里建号，邀请码在这一次里绑定。
     *
     * @param phone 手机号
     * @param code 验证码
     * @param inviteCode 邀请码，可空
     * @return 登录会话
     */
    LoginSessionDto login(String phone, String code, String inviteCode);
}
