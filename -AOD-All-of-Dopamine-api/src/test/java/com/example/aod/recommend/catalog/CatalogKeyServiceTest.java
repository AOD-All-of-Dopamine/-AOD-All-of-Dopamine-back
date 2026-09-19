package com.example.AOD.recommend.catalog;

import com.example.shared.repository.PlatformDataRepository;
import com.example.shared.repository.PlatformKeyRow;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CatalogKeyServiceTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-19T12:00:00Z");

        void advanceMillis(long millis) { now = now.plusMillis(millis); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final PlatformDataRepository repository = mock(PlatformDataRepository.class);
    private final MutableClock clock = new MutableClock();
    private final CatalogKeyService service = new CatalogKeyService(repository, clock);

    @Test
    void steamKeysAreSortedDedupedAndNewlineSeparated() {
        given(repository.findCatalogKeyRows(eq(List.of("Steam")))).willReturn(List.of(
                new PlatformKeyRow(1L, "Steam", "730", 10L),
                new PlatformKeyRow(2L, "Steam", "240", 11L),
                new PlatformKeyRow(3L, "Steam", "730", 12L),
                new PlatformKeyRow(4L, "Steam", "  ", 13L),
                new PlatformKeyRow(5L, "Steam", null, 14L)));

        assertEquals("240\n730\n", service.keysText("steam"));
    }

    @Test
    void tmdbReturnsBothMovieAndTvKeys() {
        given(repository.findCatalogKeyRows(eq(List.of("TMDB_MOVIE", "TMDB_TV")))).willReturn(List.of(
                new PlatformKeyRow(1L, "TMDB_MOVIE", "603", 10L),
                new PlatformKeyRow(2L, "TMDB_TV", "1399", 11L)));

        assertEquals("movie_603\ntv_1399\n", service.keysText("tmdb"));
    }

    @Test
    void rowsOfOtherPlatformsNeverLeakIntoTheAnswer() {
        given(repository.findCatalogKeyRows(anyCollection())).willReturn(List.of(
                new PlatformKeyRow(1L, "NaverWebtoon", "183559", 10L),
                new PlatformKeyRow(2L, "KakaoPage", "999", 11L)));

        assertEquals("183559\n", service.keysText("webtoon"));
    }

    @Test
    void emptyCatalogIsAnEmptyBody() {
        given(repository.findCatalogKeyRows(anyCollection())).willReturn(List.of());

        assertEquals("", service.keysText("webnovel"));
    }

    @Test
    void unknownPlatformThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.keysText("kakao"));
        assertThrows(IllegalArgumentException.class, () -> service.keysText(""));
        assertThrows(IllegalArgumentException.class, () -> service.keysText(null));
    }

    @Test
    void answerIsCachedUntilTheTtlRunsOut() {
        given(repository.findCatalogKeyRows(anyCollection()))
                .willReturn(List.of(new PlatformKeyRow(1L, "Steam", "730", 10L)));

        service.keysText("steam");
        service.keysText("steam");
        verify(repository, times(1)).findCatalogKeyRows(anyCollection());

        clock.advanceMillis(CatalogKeyService.TTL.toMillis());
        service.keysText("steam");
        verify(repository, times(2)).findCatalogKeyRows(anyCollection());
    }

    @Test
    void ttlIsLongEnoughToSurviveBetweenEnginePolls() {
        // 엔진 4개가 10분마다 받아 간다 — TTL 이 폴링 간격보다 짧으면 캐시가 한 번도 안 맞는다.
        assertTrue(CatalogKeyService.TTL.toMinutes() >= 5,
                "TTL 이 너무 짧으면 매 폴링이 전수 스캔이 된다");
    }

    @Test
    void concurrentCallersForOnePlatformScanOnlyOnce() throws Exception {
        CountDownLatch scanStarted = new CountDownLatch(1);
        CountDownLatch finishScan = new CountDownLatch(1);
        given(repository.findCatalogKeyRows(anyCollection())).willAnswer(invocation -> {
            scanStarted.countDown();
            finishScan.await(5, TimeUnit.SECONDS);
            return List.of(new PlatformKeyRow(1L, "Steam", "730", 10L));
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = pool.submit(() -> service.keysText("steam"));
            assertTrue(scanStarted.await(5, TimeUnit.SECONDS), "첫 스캔이 시작돼야 한다");
            Future<String> second = pool.submit(() -> service.keysText("steam"));
            Thread.sleep(100);            // 두 번째 호출이 잠금에서 기다릴 틈
            finishScan.countDown();

            assertEquals("730\n", first.get(5, TimeUnit.SECONDS));
            assertEquals("730\n", second.get(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        verify(repository, times(1)).findCatalogKeyRows(anyCollection());
    }

    @Test
    void platformsAreCachedSeparately() {
        given(repository.findCatalogKeyRows(eq(List.of("Steam"))))
                .willReturn(List.of(new PlatformKeyRow(1L, "Steam", "730", 10L)));
        given(repository.findCatalogKeyRows(eq(List.of("NaverSeries"))))
                .willReturn(List.of(new PlatformKeyRow(2L, "NaverSeries", "29494", 11L)));

        assertEquals("730\n", service.keysText("steam"));
        assertEquals("29494\n", service.keysText("webnovel"));
        assertEquals("730\n", service.keysText("steam"));

        verify(repository, times(1)).findCatalogKeyRows(eq(List.of("Steam")));
        verify(repository, times(1)).findCatalogKeyRows(eq(List.of("NaverSeries")));
    }
}
