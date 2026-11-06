package com.omino.shopai.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omino.shopai.exception.ApiBusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MallActionsTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void rejectsBannerThatHasCategory() {
        assertThrows(ApiBusinessException.class, () -> MallActions.checkEntry(
                "banner", "cat", null, "https://img.example/a.png", "none", json.createObjectNode()));
    }

    @Test
    void rejectsNavWithoutCategory() {
        assertThrows(ApiBusinessException.class, () -> MallActions.checkEntry(
                "nav", null, "美妆", "https://img.example/a.png", "none", json.createObjectNode()));
    }

    @Test
    void acceptsWebviewAndRejectsOtherSchemes() throws Exception {
        ObjectNode ok = json.createObjectNode().put("url", "https://land.example/a");
        MallActions.checkEntry("banner", null, null, "https://img.example/a.png", "webview", ok);
        ObjectNode bad = json.createObjectNode().put("url", "javascript:alert(1)");
        assertThrows(ApiBusinessException.class, () -> MallActions.checkEntry(
                "banner", null, null, "https://img.example/a.png", "webview", bad));
    }

    @Test
    void emptyAudienceMeansEveryone() {
        assertTrue(MallActions.allows("[]", "h5"));
        assertFalse(MallActions.allows("[\"android\"]", "h5"));
    }
}
