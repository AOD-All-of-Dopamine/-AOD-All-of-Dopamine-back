package com.example.crawler.common.queue.executors;

import com.example.crawler.common.queue.JobExecutor;
import com.example.crawler.common.queue.JobType;
import com.example.crawler.contents.TMDB.source.TmdbMovieSource;
import com.example.crawler.crawl.CrawlPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * TMDB 영화 크롤링 Executor — delegates to the unified CrawlPipeline + TmdbMovieSource.
 * (Removed in P4 when the Consumer talks to ContentSourceRegistry directly.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbMovieExecutor implements JobExecutor {

    private final CrawlPipeline crawlPipeline;
    private final TmdbMovieSource tmdbMovieSource;

    @Override
    public JobType getJobType() {
        return JobType.TMDB_MOVIE;
    }

    @Override
    public boolean execute(String targetId) {
        return crawlPipeline.run(tmdbMovieSource, targetId).isSuccess();
    }

    @Override
    public long getAverageExecutionTime() {
        return 800; // API 기반, 평균 800ms
    }
}
