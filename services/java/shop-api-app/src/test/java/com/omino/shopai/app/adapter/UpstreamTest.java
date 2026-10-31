package com.omino.shopai.app.adapter;

import com.omino.shopai.exception.PlatformCallException;

import com.omino.shopai.app.util.Upstream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpstreamTest {

    @Test
    void blankSecretStopsBeforeAnyCall() {
        assertThrows(PlatformCallException.class, () -> Upstream.call("key", " "));
        assertThrows(PlatformCallException.class, () -> Upstream.call("key", null));
        assertThrows(PlatformCallException.class, () -> Upstream.call((String[]) null));
    }

    @Test
    void completeSecretsPassTheGate() {
        assertDoesNotThrow(() -> Upstream.call("key", "secret", "pid"));
    }
}
