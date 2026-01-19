package com.example.crawler.common.queue.executors;

import com.example.crawler.common.queue.JobExecutor;
import com.example.crawler.common.queue.JobType;
import com.example.crawler.contents.Novel.NaverSeriesNovel.NaverSeriesCrawler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 네이버 시리즈 소설 크롤링 Executor
 * 
 * Jsoup 기반으로 매우 빠름 (~100-200ms)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NaverSeriesNovelExecutor implements JobExecutor {

    private final NaverSeriesCrawler naverSeriesCrawler;

    @Override
    public JobType getJobType() {
        return JobType.NAVER_SERIES_NOVEL;
    }

    @Override
    public boolean execute(String targetId) {
        return naverSeriesCrawler.collectNovelById(targetId);
    }

    @Override
    public long getAverageExecutionTime() {
        return 150; // Jsoup 기반, 평균 150ms
    }
}
