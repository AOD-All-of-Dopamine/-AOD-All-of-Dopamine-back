package com.example.crawler.contents.TMDB.source;

import com.example.crawler.crawl.support.GoldenFiles;
import com.example.crawler.crawl.support.SaveRawCapture;
import com.example.crawler.contents.TMDB.fetcher.TmdbApiFetcher;
import com.example.crawler.contents.TMDB.processor.TmdbPayloadProcessor;
import com.example.crawler.contents.TMDB.service.TmdbService;
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

class TmdbMovieGoldenTest {

    private static final int MOVIE_ID = 27205;

    private Map<String, Object> loadFixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/tmdb/movie-27205.json")) {
            Objects.requireNonNull(in, "fixture not found: /fixtures/tmdb/movie-27205.json");
            return new ObjectMapper().readValue(in, new TypeReference<Map<String, Object>>() {});
        }
    }

    /** Characterization of the CURRENT single-item movie path. Removed in Task 3. */
    @Test
    void legacyCollectMovieByIdMatchesGolden() throws Exception {
        TmdbApiFetcher fetcher = mock(TmdbApiFetcher.class);
        CollectorService collector = mock(CollectorService.class);
        TmdbPayloadProcessor processor = new TmdbPayloadProcessor();
        when(fetcher.getMovieDetails(eq(MOVIE_ID), eq("ko-KR"))).thenReturn(loadFixture());

        TmdbService legacy = new TmdbService(fetcher, collector, processor);
        legacy.collectMovieById(String.valueOf(MOVIE_ID));

        GoldenFiles.assertMatchesGolden("tmdb-movie-27205.json",
                SaveRawCapture.from(collector).toCanonicalJson());
    }
}
