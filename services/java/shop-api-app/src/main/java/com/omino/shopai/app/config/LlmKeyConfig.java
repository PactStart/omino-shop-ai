package com.omino.shopai.app.config;

import com.omino.shopai.support.LlmKeyCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * 把 LLM_CONFIG_KEY 交给加密器。过短也不阻止启动。
 */

@Configuration
public class LlmKeyConfig {

    @Bean
    LlmKeyCipher llmKeyCipher(@Value("${LLM_CONFIG_KEY:0123456789abcdef0123456789abcdef}") String key) {
        return new LlmKeyCipher(key);
    }
}
