package com.example.crawler.common.queue.executors;

import com.example.crawler.common.queue.JobExecutor;
import com.example.crawler.common.queue.JobType;
import com.example.crawler.contents.TMDB.service.TmdbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * TMDB TV 크롤링 Executor
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbTvExecutor implements JobExecutor {

    private final TmdbService tmdbService;

    @Override
    public JobType getJobType() {
        return JobType.TMDB_TV;
    }

    @Override
    public boolean execute(String targetId) {
        return tmdbService.collectTvShowById(targetId);
    }

    @Override
    public long getAverageExecutionTime() {
        return 800; // API 기반, 평균 800ms
    }
}
