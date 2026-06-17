package com.example.crawler.crawl;

import com.example.crawler.ingest.CollectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CrawlPipelineTest {

    private CollectorService collector;
    private CrawlPipeline pipeline;
    private FakeSource source;

    /** Configurable fake covering every branch of the pipeline. */
    static final class FakeSource implements ContentSource<String> {
        SourceDescriptor descriptor = SourceDescriptor.STEAM_GAME;
        String rawToReturn = "RAW";
        boolean throwOnFetch = false;
        CrawlPayload parseResult;
        boolean fetchCalled = false;
        boolean cleanupCalled = false;

        FakeSource() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("title", "Portal");
            this.parseResult = new CrawlPayload("400", null, payload);
        }

        public SourceDescriptor descriptor() {
            return descriptor;
        }

        public String fetchDetail(String targetId) {
            fetchCalled = true;
            if (throwOnFetch) {
                throw new RuntimeException("boom");
            }
            return rawToReturn;
        }

        public CrawlPayload parse(String targetId, String rawDetail) {
            return parseResult;
        }

        public void cleanup() {
            cleanupCalled = true;
        }
    }

    @BeforeEach
    void setUp() {
        collector = mock(CollectorService.class);
        pipeline = new CrawlPipeline(collector);
        source = new FakeSource();
    }

    @Test
    void successSavesRawWithDescriptorMetadataAndDerivedUrl() {
        CrawlResult result = pipeline.run(source, "400");

        assertThat(result.isSuccess()).isTrue();
        verify(collector).saveRaw(
                eq("Steam"), eq("GAME"), eq(source.parseResult.payload()),
                eq("400"), eq("https://store.steampowered.com/app/400"));
        assertThat(source.cleanupCalled).isTrue();
    }

    @Test
    void usesExplicitUrlWhenPayloadProvidesOne() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "X");
        source.parseResult = new CrawlPayload("400", "https://explicit/url", payload);

        pipeline.run(source, "400");

        verify(collector).saveRaw(any(), any(), any(), eq("400"), eq("https://explicit/url"));
    }

    @Test
    void blankIdSkipsWithoutFetching() {
        CrawlResult result = pipeline.run(source, "   ");

        assertThat(result.isSkipped()).isTrue();
        assertThat(source.fetchCalled).isFalse();
        verify(collector, never()).saveRaw(any(), any(), any(), any(), any());
    }

    @Test
    void nullDetailSkips() {
        source.rawToReturn = null;

        CrawlResult result = pipeline.run(source, "400");

        assertThat(result.isSkipped()).isTrue();
        verify(collector, never()).saveRaw(any(), any(), any(), any(), any());
    }

    @Test
    void nullParseSkips() {
        source.parseResult = null;

        CrawlResult result = pipeline.run(source, "400");

        assertThat(result.isSkipped()).isTrue();
        verify(collector, never()).saveRaw(any(), any(), any(), any(), any());
        assertThat(source.cleanupCalled).isTrue();
    }

    @Test
    void fetchExceptionFailsAndStillCleansUp() {
        source.throwOnFetch = true;

        CrawlResult result = pipeline.run(source, "400");

        assertThat(result.isFailed()).isTrue();
        assertThat(result.error()).isInstanceOf(RuntimeException.class);
        assertThat(source.cleanupCalled).isTrue();
        verify(collector, never()).saveRaw(any(), any(), any(), any(), any());
    }
}
