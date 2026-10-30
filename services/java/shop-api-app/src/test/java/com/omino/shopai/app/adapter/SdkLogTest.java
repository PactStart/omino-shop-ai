package com.omino.shopai.app.adapter;

import com.omino.shopai.app.adapter.platform.SdkLog;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdkLogTest {

    @Test
    void hidesSecretsInSdkText() {
        String text = SdkLog.text(Map.of("keyword", "耳机", "appSecret", "super-secret-value"));
        assertTrue(text.contains("耳机"));
        assertTrue(text.contains("***"));
        assertFalse(text.contains("super-secret-value"));
    }
}
