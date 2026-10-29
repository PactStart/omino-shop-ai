package com.omino.shopai.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ApiResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void successEnvelopeHasRequiredFields() throws Exception {
        ApiResponse<Map<String, String>> body = ApiResponse.ok(
                Map.of("goodsId", "g_jd_100012345678"),
                "01J8TRACEEXAMPLE0000000000",
                "2026-10-29T18:21:00+08:00");

        JsonNode node = mapper.readTree(mapper.writeValueAsString(body));

        assertEquals(0, node.get("code").asInt());
        assertEquals("ok", node.get("message").asText());
        assertEquals("g_jd_100012345678", node.get("data").get("goodsId").asText());
        assertFalse(node.get("traceId").asText().isBlank());
        assertFalse(node.get("ts").asText().isBlank());
    }
}
