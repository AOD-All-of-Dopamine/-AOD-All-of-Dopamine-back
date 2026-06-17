package com.example.crawler.crawl;

import com.example.crawler.common.queue.JobType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentSourceRegistryTest {

    /** Minimal fake whose only meaningful behavior is reporting its descriptor. */
    static final class StubSource implements ContentSource<String> {
        private final SourceDescriptor descriptor;

        StubSource(SourceDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        public SourceDescriptor descriptor() {
            return descriptor;
        }

        public String fetchDetail(String targetId) {
            return null;
        }

        public CrawlPayload parse(String targetId, String rawDetail) {
            return null;
        }
    }

    @Test
    void resolvesSourceByJobType() {
        StubSource steam = new StubSource(SourceDescriptor.STEAM_GAME);
        StubSource tmdb = new StubSource(SourceDescriptor.TMDB_MOVIE);
        ContentSourceRegistry registry = new ContentSourceRegistry(List.of(steam, tmdb));

        assertThat(registry.get(JobType.STEAM_GAME)).isSameAs(steam);
        assertThat(registry.get(JobType.TMDB_MOVIE)).isSameAs(tmdb);
    }

    @Test
    void throwsOnDuplicateJobType() {
        StubSource a = new StubSource(SourceDescriptor.STEAM_GAME);
        StubSource b = new StubSource(SourceDescriptor.STEAM_GAME);

        assertThatThrownBy(() -> new ContentSourceRegistry(List.of(a, b)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void throwsOnUnknownJobType() {
        ContentSourceRegistry registry = new ContentSourceRegistry(List.of());

        assertThatThrownBy(() -> registry.get(JobType.STEAM_GAME))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
