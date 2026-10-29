package com.omino.shopai.admin.config;

import com.omino.shopai.support.LlmKeyCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * 管理端使用同一把 LLM_CONFIG_KEY 解密模型密钥。
 */

@Configuration
public class AdminKeyConfig {

    @Bean
    LlmKeyCipher adminLlmKeyCipher(@Value("${LLM_CONFIG_KEY:0123456789abcdef0123456789abcdef}") String key) {
        return new LlmKeyCipher(key);
    }
}
