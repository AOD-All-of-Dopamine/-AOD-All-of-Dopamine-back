package com.example.crawler.crawl.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Golden-master file assertions.
 *
 * Default golden directory: src/test/resources/golden (resolved relative to the module working
 * directory, which is the Gradle project dir when run via :-AOD-All-of-Dopamine-crawler:test).
 *
 * Bootstrap: if the golden file does not exist, it is written and the test fails so a human
 * reviews and commits the baseline. CRLF is normalized to LF before comparison.
 */
public final class GoldenFiles {

    private static final Path DEFAULT_DIR = Paths.get("src", "test", "resources", "golden");

    private GoldenFiles() {
    }

    public static void assertMatchesGolden(String name, String actualCanonicalJson) {
        assertMatches(DEFAULT_DIR, name, actualCanonicalJson);
    }

    public static void assertMatches(Path dir, String name, String actualCanonicalJson) {
        Path file = dir.resolve(name);
        String actual = normalize(actualCanonicalJson);
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(dir);
                Files.writeString(file, actual);
                fail("Golden created at " + file.toAbsolutePath()
                        + " — review it and re-run the test.");
            }
            String expected = normalize(Files.readString(file));
            assertThat(actual).as("golden %s", file).isEqualTo(expected);
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("GoldenFiles failed for " + file, e);
        }
    }

    private static String normalize(String s) {
        return s.replace("\r\n", "\n");
    }
}
