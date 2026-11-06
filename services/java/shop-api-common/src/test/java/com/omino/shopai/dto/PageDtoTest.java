package com.omino.shopai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PageDtoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void omitsCursorWhenSearchHasNoNextPage() throws Exception {
        PageDto<String> page = new PageDto<>(List.of("a"), 1, 20, 1);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(page));

        assertEquals(1, node.get("total").asInt());
        assertFalse(node.has("nextId"));
    }

    @Test
    void writesCursorWhenSearchReturnsOne() throws Exception {
        PageDto<String> page = new PageDto<>(List.of("a"), 1, 20, 40, "pos-2");

        JsonNode node = mapper.readTree(mapper.writeValueAsString(page));

        assertEquals("pos-2", node.get("nextId").asText());
    }
}
