package com.example.crawler.common.queue.executors;

import com.example.crawler.contents.game.steam.SteamCrawlService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class SteamGameExecutorTest {

    private final SteamGameExecutor executor = new SteamGameExecutor(mock(SteamCrawlService.class));

    @Test
    void averageTimeReflectsTwoApiCallsPerGame() {
        // 게임당 appdetails + appreviews(리뷰 집계) 2회 호출 → 평균 처리시간 2배
        assertEquals(2000, executor.getAverageExecutionTime());
    }

    @Test
    void recommendedBatchSizeShrinksAccordingly() {
        // 5000ms 틱 / 2000ms = 배치 2개 (구 5개에서 축소 — rate limit 배려)
        assertEquals(2, executor.getRecommendedBatchSize());
    }
}
