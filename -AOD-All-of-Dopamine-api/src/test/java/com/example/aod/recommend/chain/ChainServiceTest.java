package com.example.AOD.recommend.chain;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChainServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-19T12:00:00Z");
    private static final OffsetDateTime NOW_ODT = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ChainService service = new ChainService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID chainId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private void stored(Chain chain) {
        given(jdbc.query(eq(ChainService.FIND_SQL), org.mockito.ArgumentMatchers.<RowMapper<Chain>>any(),
                eq(chainId))).willReturn(chain == null ? List.of() : List.of(chain));
    }

    private Chain chain(Long userId, String tab, OffsetDateTime updatedAt) {
        return new Chain(chainId, userId, tab, List.of(1L, 2L), List.of("steam:9"), 3, updatedAt);
    }

    @Test
    void findsOwnFreshChain() {
        stored(chain(7L, "all", NOW_ODT.minusHours(1)));
        assertTrue(service.find(chainId, 7L, "all").isPresent());
    }

    @Test
    void missingOtherUsersOtherTabOrExpiredChainIsEmpty() {
        stored(null);
        assertTrue(service.find(chainId, 7L, "all").isEmpty(), "행이 없다");

        stored(chain(8L, "all", NOW_ODT));
        assertTrue(service.find(chainId, 7L, "all").isEmpty(), "남의 체인");

        stored(chain(7L, "game", NOW_ODT));
        assertTrue(service.find(chainId, 7L, "all").isEmpty(), "다른 탭");

        stored(chain(7L, "all", NOW_ODT.minusHours(25)));
        assertTrue(service.find(chainId, 7L, "all").isEmpty(), "24시간 지남");

        assertTrue(service.find(null, 7L, "all").isEmpty(), "chainId 가 없으면 조회하지 않는다");
    }

    @Test
    void createWritesArraysAsLiterals() {
        Chain created = service.create(chainId, 7L, "all", List.of(3L, 1L, 3L), List.of("steam:9", "steam:9"));

        verify(jdbc).update(ChainService.INSERT_SQL, chainId, 7L, "all", "{3,1}", "{\"steam:9\"}", 0, NOW_ODT);
        assertEquals(List.of(3L, 1L), created.seenIds(), "중복은 제거한다");
        assertEquals(0, created.pageDepth());
    }

    @Test
    void appendMergesCapsAndBumpsPageDepth() {
        Chain existing = new Chain(chainId, 7L, "all", List.of(1L, 2L), List.of("steam:9"), 3, NOW_ODT.minusMinutes(5));

        Chain updated = service.append(existing, List.of(2L, 5L), List.of("tmdb:movie_1"));

        assertEquals(List.of(1L, 2L, 5L), updated.seenIds());
        assertEquals(List.of("steam:9", "tmdb:movie_1"), updated.skippedKeys());
        assertEquals(4, updated.pageDepth());
        verify(jdbc).update(ChainService.UPDATE_SQL, "{1,2,5}", "{\"steam:9\",\"tmdb:movie_1\"}", 4, NOW_ODT, chainId);
    }

    @Test
    void seenAndSkippedAreCappedKeepingTheNewest() {
        List<Long> seen = new ArrayList<>();
        for (int i = 0; i < ChainService.SEEN_MAX + 10; i++) seen.add((long) i);
        List<String> skipped = new ArrayList<>();
        for (int i = 0; i < ChainService.SKIPPED_MAX + 10; i++) skipped.add("steam:" + i);

        Chain created = service.create(chainId, 7L, "all", seen, skipped);

        assertEquals(ChainService.SEEN_MAX, created.seenIds().size());
        assertEquals(ChainService.SKIPPED_MAX, created.skippedKeys().size());
        assertEquals(10L, created.seenIds().get(0), "가장 오래된 것부터 버린다");
        assertEquals("steam:10", created.skippedKeys().get(0));
    }

    @Test
    void purgeDeletesChainsOlderThanTtl() {
        given(jdbc.update(eq(ChainService.PURGE_SQL), any(OffsetDateTime.class))).willReturn(4);

        assertEquals(4, service.purgeExpired());
        verify(jdbc).update(ChainService.PURGE_SQL, NOW_ODT.minusHours(24));
    }
}
