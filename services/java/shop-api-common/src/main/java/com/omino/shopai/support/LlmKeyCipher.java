package com.omino.shopai.support;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
/**
 * 模型密钥的 AES-GCM。材料不够就不解密。
 */

public final class LlmKeyCipher {

    private static final int IV_LENGTH = 12;
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();
/**
 * 用 AES-GCM 包住模型 Key。材料不足时加解密都失败关闭。
 */

    public LlmKeyCipher(String keyMaterial) {
        byte[] raw = keyMaterial == null ? new byte[0] : keyMaterial.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            throw new IllegalArgumentException("LLM_CONFIG_KEY 至少 32 字节");
        }
        this.key = Arrays.copyOf(raw, 32);
    }
/**
 * 加密后入库。明文不写日志。
 */

    public byte[] encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] body = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + body.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(body, 0, out, iv.length, body.length);
            return out;
        } catch (Exception ex) {
            throw new IllegalStateException("密钥加密失败");
        }
    }
/**
 * 解出明文供这一次调用。密钥材料无效则返回空。
 */

    public String decrypt(byte[] encoded) {
        if (encoded == null || encoded.length <= IV_LENGTH) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, Arrays.copyOfRange(encoded, 0, IV_LENGTH)));
            return new String(cipher.doFinal(Arrays.copyOfRange(encoded, IV_LENGTH, encoded.length)), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("密钥解密失败");
        }
    }
/**
 * 脱敏后返回。
 */

    public String mask(String plain) {
        if (plain == null || plain.length() <= 4) {
            return "****";
        }
        return plain.substring(plain.length() - 4);
    }
}
