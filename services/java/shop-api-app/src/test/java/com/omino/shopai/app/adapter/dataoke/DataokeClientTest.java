package com.omino.shopai.app.adapter.dataoke;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataokeClientTest {

    @Test
    void signSortsKeysThenUppercasesMd5() {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("version", "v1.0.2");
        params.put("appKey", "demo");
        assertEquals("B2BFA4BE020FD4B6071AE94FDBBDAE9D", DataokeClient.sign(params, "secret"));
    }

    @Test
    void missingSecretReturnsEmptyWithoutCalling() {
        DataokeClient client = new DataokeClient("", "");
        assertEquals(List.of(), client.suggestions("纸巾"));
        assertEquals(List.of(), client.hotWords());
    }
}
