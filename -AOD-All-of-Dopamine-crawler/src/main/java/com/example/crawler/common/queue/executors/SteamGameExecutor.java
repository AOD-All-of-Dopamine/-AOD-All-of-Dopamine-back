package com.example.crawler.common.queue.executors;

import com.example.crawler.common.queue.JobExecutor;
import com.example.crawler.common.queue.JobType;
import com.example.crawler.game.steam.service.SteamCrawlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Steam 게임 크롤링 Executor
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SteamGameExecutor implements JobExecutor {

    private final SteamCrawlService steamCrawlService;

    @Override
    public JobType getJobType() {
        return JobType.STEAM_GAME;
    }

    @Override
    public boolean execute(String targetId) {
        return steamCrawlService.collectGameByAppId(Long.parseLong(targetId));
    }

    @Override
    public long getAverageExecutionTime() {
        return 1000; // API 기반, 평균 1초
    }
}
