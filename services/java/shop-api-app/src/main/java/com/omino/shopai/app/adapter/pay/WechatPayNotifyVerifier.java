package com.omino.shopai.app.adapter.pay;

import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RSAPublicKeyNotificationConfig;
import com.wechat.pay.java.core.notification.RequestParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
/**
 * 微信支付回调验签。密钥为空则一律拒绝。
 */

@Component
public class WechatPayNotifyVerifier implements PayNotice {

    private final String publicKey;
    private final String publicKeyId;
    private final String apiV3Key;

    public WechatPayNotifyVerifier(
            @Value("${PAY_WECHAT_PUBLIC_KEY:}") String publicKey,
            @Value("${PAY_WECHAT_PUBLIC_KEY_ID:}") String publicKeyId,
            @Value("${PAY_WECHAT_API_V3_KEY:}") String apiV3Key) {
        this.publicKey = publicKey;
        this.publicKeyId = publicKeyId;
        this.apiV3Key = apiV3Key;
    }

    @Override
    public boolean accepted(String body) {
        return accepted(null, null, null, null, body);
    }
/**
 * 验签失败或密钥为空则拒绝入账。不向微信网关发探测请求。
 */

    public boolean accepted(String serial, String timestamp, String nonce, String signature, String body) {
        if (blank(publicKey) || blank(publicKeyId) || blank(apiV3Key) || blank(body)) {
            return false;
        }
        if (blank(serial) || blank(timestamp) || blank(nonce) || blank(signature)) {
            return false;
        }
        try {
            RSAPublicKeyNotificationConfig config = new RSAPublicKeyNotificationConfig.Builder()
                    .publicKey(publicKey)
                    .publicKeyId(publicKeyId)
                    .apiV3Key(apiV3Key)
                    .build();
            RequestParam param = new RequestParam.Builder()
                    .serialNumber(serial)
                    .timestamp(timestamp)
                    .nonce(nonce)
                    .signature(signature)
                    .body(body)
                    .build();
            new NotificationParser(config).parse(param, Map.class);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
