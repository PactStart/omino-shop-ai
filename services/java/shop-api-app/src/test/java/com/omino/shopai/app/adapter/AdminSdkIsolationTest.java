package com.omino.shopai.app.adapter;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AdminSdkIsolationTest {

    @Test
    void adminDoesNotReferenceUnionSdks() throws Exception {
        String pom = Files.readString(Path.of("../shop-api-admin/pom.xml"));
        assertFalse(pom.contains("com.taobao"));
        assertFalse(pom.contains("com.jd"));
        assertFalse(pom.contains("com.pdd"));
        assertFalse(pom.contains("com.vip"));
        try (Stream<Path> files = Files.walk(Path.of("../shop-api-admin/src"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                assertFalse(text.contains("com.taobao"), file.toString());
                assertFalse(text.contains("com.jd.open"), file.toString());
                assertFalse(text.contains("com.pdd.pop"), file.toString());
                assertFalse(text.contains("com.vip."), file.toString());
            }
        }
    }
}
