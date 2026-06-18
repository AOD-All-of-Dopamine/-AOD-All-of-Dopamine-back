package com.example.crawler.common.queue.executors;

import com.example.crawler.common.queue.JobExecutor;
import com.example.crawler.common.queue.JobType;
import com.example.crawler.contents.TMDB.source.TmdbTvSource;
import com.example.crawler.crawl.CrawlPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * TMDB TV 크롤링 Executor — delegates to the unified CrawlPipeline + TmdbTvSource.
 * (Removed in P4 when the Consumer talks to ContentSourceRegistry directly.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbTvExecutor implements JobExecutor {

    private final CrawlPipeline crawlPipeline;
    private final TmdbTvSource tmdbTvSource;

    @Override
    public JobType getJobType() {
        return JobType.TMDB_TV;
    }

    @Override
    public boolean execute(String targetId) {
        return crawlPipeline.run(tmdbTvSource, targetId).isSuccess();
    }

    @Override
    public long getAverageExecutionTime() {
        return 800; // API 기반, 평균 800ms
    }
}
