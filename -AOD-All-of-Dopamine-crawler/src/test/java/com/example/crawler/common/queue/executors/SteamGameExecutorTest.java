package com.example.crawler.common.queue.executors;

import com.example.crawler.contents.game.steam.SteamFetcher;
import com.example.crawler.contents.game.steam.SteamPayloadProcessor;
import com.example.crawler.ingest.CollectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SteamGameExecutorTest {

    private SteamFetcher fetcher;
    private CollectorService collectorService;
    private SteamGameExecutor executor;

    @BeforeEach
    void setUp() {
        fetcher = mock(SteamFetcher.class);
        collectorService = mock(CollectorService.class);
        executor = new SteamGameExecutor(fetcher, new SteamPayloadProcessor(), collectorService);
    }

    private Map<String, Object> gameDetails() {
        Map<String, Object> details = new HashMap<>();
        details.put("type", "game");
        details.put("name", "Half-Life");
        return details;
    }

    @Test
    @SuppressWarnings("unchecked")
    void mergesReviewSummaryIntoSavedPayload() {
        when(fetcher.fetchGameDetails(70L)).thenReturn(gameDetails());
        Map<String, Object> summary = Map.of("review_score", 9, "total_reviews", 31892);
        when(fetcher.fetchReviewSummary(70L)).thenReturn(summary);

        assertTrue(executor.execute("70"));

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(collectorService).saveRaw(eq("Steam"), eq("GAME"), payload.capture(), eq("70"), anyString());
        assertEquals(summary, payload.getValue().get("review_summary"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void savesWithoutReviewSummaryWhenUnavailable() {
        when(fetcher.fetchGameDetails(70L)).thenReturn(gameDetails());
        when(fetcher.fetchReviewSummary(70L)).thenReturn(null);

        assertTrue(executor.execute("70"));

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(collectorService).saveRaw(eq("Steam"), eq("GAME"), payload.capture(), eq("70"), anyString());
        assertFalse(payload.getValue().containsKey("review_summary"), "요약 실패 시 키 자체가 없어야 함");
    }

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
