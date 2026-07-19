package com.example.crawler.contents.game.steam;

import com.example.crawler.ingest.CollectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SteamCrawlServiceTest {

    private SteamApiFetcher fetcher;
    private CollectorService collectorService;
    private SteamCrawlService service;

    @BeforeEach
    void setUp() {
        fetcher = mock(SteamApiFetcher.class);
        collectorService = mock(CollectorService.class);
        service = new SteamCrawlService(fetcher, collectorService, new SteamPayloadProcessor());
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

        assertTrue(service.collectGameByAppId(70L));

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(collectorService).saveRaw(eq("Steam"), eq("GAME"), payload.capture(), eq("70"), anyString());
        assertEquals(summary, payload.getValue().get("review_summary"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void savesWithoutReviewSummaryWhenUnavailable() {
        when(fetcher.fetchGameDetails(70L)).thenReturn(gameDetails());
        when(fetcher.fetchReviewSummary(70L)).thenReturn(null);

        assertTrue(service.collectGameByAppId(70L));

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(collectorService).saveRaw(eq("Steam"), eq("GAME"), payload.capture(), eq("70"), anyString());
        assertFalse(payload.getValue().containsKey("review_summary"), "요약 실패 시 키 자체가 없어야 함");
    }
}
