package com.omino.shopai.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.support.LlmKeyCipher;
import com.omino.shopai.support.ModelSecret;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

class SecretLeakTest {

    @Test
    void samplesOmitModelKeyUnionPidAndFullPayee() throws Exception {
        String plain = "sk-live-secret-value";
        LlmKeyCipher cipher = new LlmKeyCipher("0123456789abcdef0123456789abcdef");
        String model = new ObjectMapper().writeValueAsString(ModelSecret.view(cipher.encrypt(plain), cipher));
        assertFalse(model.contains(plain));
        String account = new ObjectMapper().writeValueAsString(Map.of("accountMask", "138****8000"));
        assertFalse(account.contains("13800138000"));
        String internal = Files.readString(Path.of("src/main/java/com/omino/shopai/app/service/auth/InternalAccountService.java"));
        assertFalse(internal.contains("\"relationId\""));
        assertFalse(internal.contains("\"relation_id\""));
        assertFalse(internal.contains("\"pid\""));
        assertFalse(internal.contains("\"PID\""));
        String goods = Files.readString(Path.of("src/main/java/com/omino/shopai/app/web/internal/InternalGoodsController.java"));
        assertFalse(goods.contains("convert-link"));
        String credits = Files.readString(Path.of("src/main/java/com/omino/shopai/app/web/internal/InternalAccountController.java"));
        assertFalse(credits.contains("debit"));
        assertFalse(credits.contains("IAgentGateway"));
        for (String path : new String[] {
                "/internal/v1/goods/search",
                "/internal/v1/me",
                "/internal/v1/orders",
                "/internal/v1/wallet",
                "/internal/v1/credits"
        }) {
            assertFalse(missing(path), path);
        }
    }

    private static boolean missing(String path) throws Exception {
        String goods = Files.readString(Path.of("src/main/java/com/omino/shopai/app/web/internal/InternalGoodsController.java"));
        String account = Files.readString(Path.of("src/main/java/com/omino/shopai/app/web/internal/InternalAccountController.java"));
        return !goods.contains(path.replace("/internal/v1/goods", "")) && !account.contains(path.replace("/internal/v1", ""));
    }
}
