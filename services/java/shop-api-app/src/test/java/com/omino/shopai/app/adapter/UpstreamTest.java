package com.omino.shopai.app.adapter;

import com.omino.shopai.app.adapter.platform.Upstream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpstreamTest {

    @Test
    void blankSecretStopsBeforeAnyCall() {
        assertThrows(com.omino.shopai.app.adapter.platform.PlatformCallException.class, () -> Upstream.call("key", " "));
        assertThrows(com.omino.shopai.app.adapter.platform.PlatformCallException.class, () -> Upstream.call("key", null));
        assertThrows(com.omino.shopai.app.adapter.platform.PlatformCallException.class, () -> Upstream.call((String[]) null));
    }

    @Test
    void completeSecretsPassTheGate() {
        assertDoesNotThrow(() -> Upstream.call("key", "secret", "pid"));
    }
}
