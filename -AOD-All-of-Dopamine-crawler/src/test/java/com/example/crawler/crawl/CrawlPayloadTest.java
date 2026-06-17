package com.example.crawler.crawl;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrawlPayloadTest {

    @Test
    void ofBuildsPayloadWithNullUrl() {
        CrawlPayload p = CrawlPayload.of("400", Map.of("title", "Portal"));
        assertThat(p.platformSpecificId()).isEqualTo("400");
        assertThat(p.url()).isNull();
        assertThat(p.payload()).containsEntry("title", "Portal");
    }

    @Test
    void rejectsBlankPlatformSpecificId() {
        assertThatThrownBy(() -> new CrawlPayload("  ", null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullPayload() {
        assertThatThrownBy(() -> new CrawlPayload("400", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
