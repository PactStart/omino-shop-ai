package com.omino.shopai.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapperXmlGeneratorTest {

    private final Path module = Path.of("").toAbsolutePath();

    @Test
    void committedAppUserXmlHasNoAffiliateBranch() throws IOException {
        Path xml = module.resolve("src/main/resources/mapper/AppUserMapper.xml");
        assertTrue(Files.isRegularFile(xml));
        String text = Files.readString(xml);
        assertFalse(text.contains("联盟"));
        assertFalse(text.toLowerCase().contains("affiliate"));
        assertFalse(text.contains("relation_id"));
    }

    @Test
    void generatorDoesNotEmitEntityOrService() throws IOException {
        Path source = module.resolve("src/test/java/com/omino/shopai/mapper/MapperXmlGenerator.java");
        String generator = Files.readString(source);
        assertTrue(generator.contains("TemplateType.ENTITY"));
        assertTrue(generator.contains("TemplateType.SERVICE"));
        assertTrue(generator.contains("TemplateType.CONTROLLER"));
        assertTrue(generator.contains(".custom.xml"));
        try (Stream<Path> walk = Files.walk(module.resolve("src/main/java"))) {
            assertTrue(walk.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .noneMatch(name -> name.endsWith("Service.java") || name.endsWith("Controller.java")));
        }
    }
}
