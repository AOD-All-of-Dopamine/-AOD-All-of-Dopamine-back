package com.example.crawler.contents.TMDB.source;

import com.example.crawler.crawl.CrawlPipeline;
import com.example.crawler.crawl.support.GoldenFiles;
import com.example.crawler.crawl.support.SaveRawCapture;
import com.example.crawler.contents.TMDB.fetcher.TmdbApiFetcher;
import com.example.crawler.contents.TMDB.processor.TmdbPayloadProcessor;
import com.example.crawler.ingest.CollectorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TmdbTvGoldenTest {

    private static final int TV_ID = 1396;

    private Map<String, Object> loadFixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/tmdb/tv-1396.json")) {
            Objects.requireNonNull(in, "fixture not found: /fixtures/tmdb/tv-1396.json");
            return new ObjectMapper().readValue(in, new TypeReference<Map<String, Object>>() {});
        }
    }

    @Test
    void tmdbTvSourceMatchesGolden() throws Exception {
        TmdbApiFetcher fetcher = mock(TmdbApiFetcher.class);
        CollectorService collector = mock(CollectorService.class);
        TmdbPayloadProcessor processor = new TmdbPayloadProcessor();
        when(fetcher.getTvShowDetails(eq(TV_ID), eq("ko-KR"))).thenReturn(loadFixture());

        TmdbTvSource source = new TmdbTvSource(fetcher, processor);
        new CrawlPipeline(collector).run(source, String.valueOf(TV_ID));

        GoldenFiles.assertMatchesGolden("tmdb-tv-1396.json",
                SaveRawCapture.from(collector).toCanonicalJson());
    }
}
