package com.omino.shopai.app.adapter;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdkArtifactsTest {

    private static final Path SDKS = Path.of("../sdks");

    @Test
    void vendorJarsArePresentWithoutCopiedCallSites() throws Exception {
        assertTrue(Files.isRegularFile(match("taobao-sdk-java-auto_1740648016443-20250925.jar")));
        assertTrue(Files.isRegularFile(match("taobao-sdk-java-auto_1740648016443-20250925-source.jar")));
        assertTrue(Files.isRegularFile(match("jd-api-sdk-java-20250925.jar")));
        assertTrue(Files.isRegularFile(match("jd-api-sdk-java-20250925.pom")));
        assertTrue(Files.isRegularFile(match("pop-sdk-1.10.16.jar")));
        assertTrue(Files.isRegularFile(match("pop-sdk-1.10.16.pom")));
        assertTrue(Files.isRegularFile(match("vop-sdk-20250214094310.jar")));
        assertTrue(Files.isRegularFile(match("osp-sdk-20250214094310.jar")));
        try (Stream<Path> files = Files.walk(SDKS)) {
            List<Path> javaFiles = files.filter(path -> path.getFileName().toString().endsWith(".java")).toList();
            assertTrue(javaFiles.isEmpty());
        }
        assertFalse(Files.exists(SDKS.resolve("install-jar.sh")));
        assertFalse(Files.exists(SDKS.resolve("deploy-jar.sh")));
        assertTrue(Files.isExecutable(SDKS.resolve("install-local.sh")));
        assertTrue(Files.isExecutable(SDKS.resolve("deploy-private.sh")));
    }

    @Test
    void lockedStackVersionsStayPut() throws Exception {
        String pom = Files.readString(Path.of("../pom.xml"));
        assertTrue(pom.contains("<version>3.4.11</version>"));
        assertTrue(pom.contains("<mybatis-plus.version>3.5.9</mybatis-plus.version>"));
        assertTrue(pom.contains("com.taobao"));
        assertTrue(pom.contains("open-api-sdk"));
        String app = Files.readString(Path.of("pom.xml"));
        assertTrue(app.contains("taobao-sdk-java"));
        assertTrue(app.contains("osp-sdk"));
        String admin = Files.readString(Path.of("../shop-api-admin/pom.xml"));
        assertFalse(admin.contains("taobao-sdk-java"));
        assertFalse(admin.contains("open-api-sdk"));
        assertFalse(admin.contains("pop-sdk"));
        assertFalse(admin.contains("vop-sdk"));
        assertFalse(admin.contains("osp-sdk"));
    }

    @Test
    void deployScriptRefusesASettingsFileWithoutTheRepository() throws Exception {
        String script = Files.readString(SDKS.resolve("deploy-private.sh"));
        assertFalse(script.contains("http://"));
        assertFalse(script.contains("https://"));
        assertFalse(script.contains("password"));
        assertFalse(script.contains("UNION_"));
        Path home = Files.createTempDirectory("sdk-settings");
        Files.createDirectories(home.resolve(".m2"));
        Files.writeString(home.resolve(".m2/settings.xml"), "<settings></settings>");
        ProcessBuilder builder = new ProcessBuilder("sh", SDKS.resolve("deploy-private.sh").toAbsolutePath().toString());
        builder.directory(SDKS.toAbsolutePath().toFile());
        builder.environment().put("HOME", home.toAbsolutePath().toString());
        builder.redirectErrorStream(true);
        Process checked = builder.start();
        String output = new String(checked.getInputStream().readAllBytes());
        int code = checked.waitFor();
        assertFalse(output.contains("UNION_"));
        assertTrue(code != 0);
    }

    private static Path match(String name) {
        Path path = SDKS.resolve(name);
        assertTrue(Files.isRegularFile(path), name);
        return path;
    }
}
