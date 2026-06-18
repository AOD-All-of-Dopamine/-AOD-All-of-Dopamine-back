package com.example.crawler.common.queue.executors;

import com.example.crawler.common.queue.JobExecutor;
import com.example.crawler.common.queue.JobType;
import com.example.crawler.crawl.CrawlPipeline;
import com.example.crawler.game.steam.source.SteamGameSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Steam 게임 크롤링 Executor — delegates to the unified CrawlPipeline + SteamGameSource.
 * (This thin executor is removed in P4 when the Consumer talks to ContentSourceRegistry directly.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SteamGameExecutor implements JobExecutor {

    private final CrawlPipeline crawlPipeline;
    private final SteamGameSource steamGameSource;

    @Override
    public JobType getJobType() {
        return JobType.STEAM_GAME;
    }

    @Override
    public boolean execute(String targetId) {
        return crawlPipeline.run(steamGameSource, targetId).isSuccess();
    }

    @Override
    public long getAverageExecutionTime() {
        return 1000; // API 기반, 평균 1초
    }
}
