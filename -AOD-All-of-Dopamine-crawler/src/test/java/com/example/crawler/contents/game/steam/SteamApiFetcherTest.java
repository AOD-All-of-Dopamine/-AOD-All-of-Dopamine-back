package com.example.crawler.contents.game.steam;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SteamApiFetcherTest {

    private RestTemplate restTemplate;
    private SteamRateLimiter rateLimiter;
    private SteamApiFetcher fetcher;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        rateLimiter = mock(SteamRateLimiter.class);
        fetcher = new SteamApiFetcher(restTemplate, new ObjectMapper(), rateLimiter);
    }

    @Test
    void fetchReviewSummaryReturnsSummaryWithoutPageNoiseField() {
        when(restTemplate.getForObject(anyString(), eq(String.class), eq(70L))).thenReturn("""
                {"success":1,"query_summary":{
                  "num_reviews":0,"review_score":9,"review_score_desc":"Overwhelmingly Positive",
                  "total_positive":31288,"total_negative":604,"total_reviews":31892}}
                """);

        Map<String, Object> summary = fetcher.fetchReviewSummary(70L);

        assertNotNull(summary);
        assertEquals(9, summary.get("review_score"));
        assertEquals(31892, summary.get("total_reviews"));
        assertEquals("Overwhelmingly Positive", summary.get("review_score_desc"));
        assertFalse(summary.containsKey("num_reviews"), "페이지 노이즈(num_reviews)는 제거되어야 함");
        verify(rateLimiter).acquirePermit();
    }

    @Test
    void fetchReviewSummaryReturnsNullWhenSummaryMissing() {
        when(restTemplate.getForObject(anyString(), eq(String.class), eq(999L)))
                .thenReturn("{\"success\":2}");

        assertNull(fetcher.fetchReviewSummary(999L));
    }

    @Test
    void fetchReviewSummaryReturnsNullOnHttpFailureInsteadOfThrowing() {
        when(restTemplate.getForObject(anyString(), eq(String.class), eq(70L)))
                .thenThrow(new RestClientException("timeout"));

        assertNull(fetcher.fetchReviewSummary(70L), "요약 조회 실패가 수집을 막으면 안 됨");
    }
}
