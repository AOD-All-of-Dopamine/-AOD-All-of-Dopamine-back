package com.example.crawler.contents.TMDB.source;

import com.example.crawler.contents.TMDB.fetcher.TmdbApiFetcher;
import com.example.crawler.contents.TMDB.processor.TmdbPayloadProcessor;
import com.example.crawler.crawl.ContentSource;
import com.example.crawler.crawl.CrawlPayload;
import com.example.crawler.crawl.SourceDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * TMDB movie ContentSource. Fetch delegates to TmdbApiFetcher.getMovieDetails (language ko-KR,
 * matching the legacy single-item path), parse delegates to TmdbPayloadProcessor.
 */
@Component
@RequiredArgsConstructor
public class TmdbMovieSource implements ContentSource<Map<String, Object>> {

    private static final String LANGUAGE = "ko-KR";

    private final TmdbApiFetcher tmdbApiFetcher;
    private final TmdbPayloadProcessor payloadProcessor;

    @Override
    public SourceDescriptor descriptor() {
        return SourceDescriptor.TMDB_MOVIE;
    }

    @Override
    public Map<String, Object> fetchDetail(String targetId) {
        return tmdbApiFetcher.getMovieDetails(Integer.parseInt(targetId), LANGUAGE);
    }

    @Override
    public CrawlPayload parse(String targetId, Map<String, Object> rawDetail) {
        if (rawDetail.isEmpty()) {
            return null; // no data → skip (matches legacy null/empty guard)
        }
        return new CrawlPayload(targetId, null, payloadProcessor.process(rawDetail));
    }
}
