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
        return 2000; // 🚀 150ms → 2000ms (배치 크기 축소 위해 늘림)
    }
    
    @Override
    public int getRecommendedBatchSize() {
        return 3; // 🚀 20개 → 3개 (스레드 과다 생성 방지)
    }
}
