package com.omino.shopai.app.adapter.pay;

import com.alipay.api.internal.util.AlipaySignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
/**
 * 支付宝回调验签。密钥为空则一律拒绝。
 */

@Component
public class AlipayNotifyVerifier implements PayNotice {

    private final String publicKey;

    public AlipayNotifyVerifier(@Value("${PAY_ALIPAY_PUBLIC_KEY:}") String publicKey) {
        this.publicKey = publicKey;
    }
/**
 * 验签失败或密钥为空则拒绝入账。不向支付宝发探测请求。
 */

    @Override
    public boolean accepted(String body) {
        if (publicKey == null || publicKey.isBlank() || body == null || body.isBlank()) {
            return false;
        }
        try {
            return AlipaySignature.rsaCheckV1(form(body), publicKey, "UTF-8", "RSA2");
        } catch (Exception ex) {
            return false;
        }
    }

    private static Map<String, String> form(String body) {
        Map<String, String> params = new HashMap<>();
        for (String pair : body.split("&")) {
            int cut = pair.indexOf('=');
            if (cut <= 0) {
                continue;
            }
            params.put(decode(pair.substring(0, cut)), decode(pair.substring(cut + 1)));
        }
        return params;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
