package com.example.crawler.crawl.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoldenFilesTest {

    @Test
    void bootstrapsWhenMissingThenFails(@TempDir Path dir) {
        assertThatThrownBy(() -> GoldenFiles.assertMatches(dir, "sample.json", "{\n  \"a\" : 1\n}"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Golden created");

        assertThat(Files.exists(dir.resolve("sample.json"))).isTrue();
    }

    @Test
    void passesWhenContentMatches(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("sample.json"), "{\n  \"a\" : 1\n}");

        GoldenFiles.assertMatches(dir, "sample.json", "{\n  \"a\" : 1\n}");
    }

    @Test
    void ignoresCrlfDifferences(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("sample.json"), "{\r\n  \"a\" : 1\r\n}");

        GoldenFiles.assertMatches(dir, "sample.json", "{\n  \"a\" : 1\n}");
    }

    @Test
    void failsWhenContentDiffers(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("sample.json"), "{\n  \"a\" : 1\n}");

        assertThatThrownBy(() -> GoldenFiles.assertMatches(dir, "sample.json", "{\n  \"a\" : 2\n}"))
                .isInstanceOf(AssertionError.class);
    }
}
