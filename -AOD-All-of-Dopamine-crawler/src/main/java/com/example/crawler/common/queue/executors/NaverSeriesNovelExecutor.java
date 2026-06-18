package com.example.crawler.common.queue.executors;

import com.example.crawler.common.queue.JobExecutor;
import com.example.crawler.common.queue.JobType;
import com.example.crawler.contents.Novel.NaverSeriesNovel.NaverSeriesNovelSource;
import com.example.crawler.crawl.CrawlPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 네이버 시리즈 소설 크롤링 Executor — delegates to the unified CrawlPipeline + NaverSeriesNovelSource.
 * (Removed in P4 when the Consumer talks to ContentSourceRegistry directly.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NaverSeriesNovelExecutor implements JobExecutor {

    private final CrawlPipeline crawlPipeline;
    private final NaverSeriesNovelSource naverSeriesNovelSource;

    @Override
    public JobType getJobType() {
        return JobType.NAVER_SERIES_NOVEL;
    }

    @Override
    public boolean execute(String targetId) {
        return crawlPipeline.run(naverSeriesNovelSource, targetId).isSuccess();
    }

    @Override
    public long getAverageExecutionTime() {
        return 2000;
    }

    @Override
    public int getRecommendedBatchSize() {
        return 3;
    }
}
