package com.omino.shopai.support;
/**
 * 模型密钥的替换和掩码。明文不进响应。
 */

public final class ModelSecret {

    private ModelSecret() {
    }
/**
 * 空串表示沿用旧密钥。新值才加密替换，避免编辑时把 Key 清空。
 */

    public static byte[] keepOrReplace(byte[] current, String newKey, LlmKeyCipher cipher) {
        if (newKey == null || newKey.isBlank()) {
            return current;
        }
        return cipher.encrypt(newKey);
    }
/**
 * 掩码只露末四位。过短则整段打成星号。
 */

    public static ModelKeyView view(byte[] encoded, LlmKeyCipher cipher) {
        boolean set = encoded != null && encoded.length > 0;
        String plain = set ? cipher.decrypt(encoded) : null;
        return new ModelKeyView(set, plain == null ? null : cipher.mask(plain));
    }
/**
 * 密钥是否已设置，以及掩码。不含明文。
 */

    public record ModelKeyView(boolean apiKeySet, String apiKeyMask) {
    }
}
