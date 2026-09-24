package com.java2okf.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    private final ConfigLoader loader = new ConfigLoader();

    @Test
    void nullPathYieldsDefaults() {
        Java2OkfConfig config = loader.load(null);
        assertEquals("./output", config.getOutput().getDirectory());
        assertTrue(config.getOkf().isGenerateMethodDocuments(), "method documents are enabled by default");
        assertEquals(List.of(".java"), config.getScanner().getExtensions());
        assertTrue(config.getScanner().getExcludeDirectories().containsAll(List.of("target", "build", "out", ".git", "generated")));
    }

    @Test
    void fileValuesOverrideDefaultsAndMissingSectionsKeepDefaults() throws IOException {
        Path file = write("""
                project:
                  name: demo
                  sourceRoot: ./src
                okf:
                  generateMethodDocuments: false
                """);
        Java2OkfConfig config = loader.load(file);
        assertEquals("demo", config.getProject().getName());
        assertEquals("./src", config.getProject().getSourceRoot());
        assertFalse(config.getOkf().isGenerateMethodDocuments());
        assertTrue(config.getOkf().isGenerateIndexes(), "unspecified values keep defaults");
        assertEquals("INFO", config.getLogging().getLevel());
    }

    @Test
    void unknownPropertyIsRejected() throws IOException {
        Path file = write("""
                okf:
                  generateMethodDocs: false
                """);
        ConfigException e = assertThrows(ConfigException.class, () -> loader.load(file));
        assertTrue(e.getMessage().contains("generateMethodDocs"), e.getMessage());
    }

    @Test
    void malformedYamlIsRejected() throws IOException {
        Path file = write("project: [unclosed\n");
        assertThrows(ConfigException.class, () -> loader.load(file));
    }

    @Test
    void missingFileIsRejected() {
        assertThrows(ConfigException.class, () -> loader.load(tempDir.resolve("absent.yaml")));
    }

    @Test
    void emptyFileYieldsDefaults() throws IOException {
        assertEquals("./output", loader.load(write("")).getOutput().getDirectory());
        assertEquals("./output", loader.load(write("# only a comment\n")).getOutput().getDirectory());
    }

    @Test
    void cliOverridesWinOverFileValues() throws IOException {
        Path file = write("""
                project:
                  sourceRoot: ./from-file
                output:
                  directory: ./file-output
                performance:
                  threadCount: 8
                """);
        Java2OkfConfig config = loader.load(file);
        new ConfigOverrides("./from-cli", null, null, true, null, null, false, 1, "DEBUG", null, null)
                .applyTo(config);

        assertEquals("./from-cli", config.getProject().getSourceRoot());
        assertEquals("./file-output", config.getOutput().getDirectory(), "unspecified CLI values keep file values");
        assertTrue(config.getOutput().isCleanBeforeGenerate());
        assertFalse(config.getOkf().isIncludeGenerationTimestamp());
        assertEquals(1, config.getPerformance().effectiveThreadCount());
        assertEquals("DEBUG", config.getLogging().getLevel());
    }

    @Test
    void validatorRejectsMissingSource() {
        Java2OkfConfig config = Java2OkfConfig.defaults();
        assertThrows(ConfigException.class, () -> new ConfigValidator().validateForAnalysis(config));
    }

    @Test
    void validatorRejectsUnsupportedOkfVersion() {
        Java2OkfConfig config = Java2OkfConfig.defaults();
        config.getProject().setSourceRoot(".");
        config.getOkf().setVersion("9.9");
        assertThrows(ConfigException.class, () -> new ConfigValidator().validateForAnalysis(config));
    }

    @Test
    void automaticThreadCountIsBounded() {
        PerformanceSettings settings = new PerformanceSettings();
        int threads = settings.effectiveThreadCount();
        assertTrue(threads >= 1 && threads <= PerformanceSettings.MAX_AUTO_THREADS);
    }

    private Path write(String yaml) throws IOException {
        Path file = tempDir.resolve("java2okf.yaml");
        Files.writeString(file, yaml);
        return file;
    }
}
