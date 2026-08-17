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
    void skipsAdultGameBeforeSaveAndCounts() {
        // required_age "18"(문자열) — appdetails는 숫자·문자열 혼재 응답
        Map<String, Object> details = gameDetails();
        details.put("required_age", "18");
        when(fetcher.fetchGameDetails(70L)).thenReturn(details);

        assertTrue(executor.execute("70"), "의도된 스킵은 작업 성공 (false면 재시도됨)");

        verify(collectorService, never()).saveRaw(anyString(), anyString(), any(), anyString(), anyString());
        verify(fetcher, never()).fetchReviewSummary(anyLong()); // 스킵이면 리뷰 API 호출도 아낀다
        assertEquals(1, executor.getAdultSkipCount());
    }

    @Test
    void skipsAdultGameWithNumericRequiredAge() {
        Map<String, Object> details = gameDetails();
        details.put("required_age", 19); // 국내 심의 등 18 초과값도 성인
        when(fetcher.fetchGameDetails(70L)).thenReturn(details);

        assertTrue(executor.execute("70"));
        verify(collectorService, never()).saveRaw(anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void underageRequiredAgeStillSaved() {
        Map<String, Object> details = gameDetails();
        details.put("required_age", "17"); // 18 미만 경계값 — 저장되어야 함
        when(fetcher.fetchGameDetails(70L)).thenReturn(details);
        when(fetcher.fetchReviewSummary(70L)).thenReturn(null);

        assertTrue(executor.execute("70"));
        verify(collectorService).saveRaw(eq("Steam"), eq("GAME"), any(), eq("70"), anyString());
        assertEquals(0, executor.getAdultSkipCount());
    }

    @Test
    void parseRequiredAgeAbsorbsMixedFormats() {
        assertEquals(18, SteamGameExecutor.parseRequiredAge("18"));
        assertEquals(18, SteamGameExecutor.parseRequiredAge("18+"));
        assertEquals(18, SteamGameExecutor.parseRequiredAge(18));
        assertEquals(0, SteamGameExecutor.parseRequiredAge(0));
        assertEquals(0, SteamGameExecutor.parseRequiredAge(null));   // 부재 = 전체이용가 취급
        assertEquals(0, SteamGameExecutor.parseRequiredAge("abc")); // 파싱 불가 = 전체이용가 취급
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
