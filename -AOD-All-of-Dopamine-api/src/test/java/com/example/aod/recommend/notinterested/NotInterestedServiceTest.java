package com.example.AOD.recommend.notinterested;

import com.example.AOD.recommend.context.RecContext;
import com.example.AOD.recommend.log.RecEventRecorder;
import com.example.AOD.recommend.reaction.ContentNotFoundException;
import com.example.shared.repository.ContentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class NotInterestedServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-19T12:00:00Z");
    private static final OffsetDateTime NOW_ODT = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ContentRepository contents = mock(ContentRepository.class);
    private final RecEventRecorder recorder = mock(RecEventRecorder.class);
    private final NotInterestedService service =
            new NotInterestedService(jdbc, contents, recorder, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void turnOnInsertsAndEmitsEventOnce() {
        given(contents.existsById(42L)).willReturn(true);
        given(jdbc.update(NotInterestedService.UPSERT_SQL, 7L, 42L, NOW_ODT)).willReturn(1, 0);

        assertTrue(service.turnOn(7L, 42L));
        assertTrue(service.turnOn(7L, 42L), "멱등 — 두 번째도 켜진 상태");

        verify(recorder).notInterestedChanged(eq(7L), eq(42L), eq(true), any(RecContext.class));
    }

    @Test
    void turnOffDeletesAndEmitsEventOnce() {
        given(contents.existsById(42L)).willReturn(true);
        given(jdbc.update(NotInterestedService.DELETE_SQL, 7L, 42L)).willReturn(1, 0);

        assertFalse(service.turnOff(7L, 42L));
        assertFalse(service.turnOff(7L, 42L));

        verify(recorder).notInterestedChanged(eq(7L), eq(42L), eq(false), any(RecContext.class));
    }

    @Test
    void unknownContentIs404() {
        given(contents.existsById(99L)).willReturn(false);

        assertThrows(ContentNotFoundException.class, () -> service.turnOn(7L, 99L));
        assertThrows(ContentNotFoundException.class, () -> service.turnOff(7L, 99L));
        verify(recorder, never()).notInterestedChanged(anyLong(), anyLong(), anyBoolean(), any(RecContext.class));
    }

    @Test
    void activeIdsOnlyLookAtTheLastNinetyDays() {
        given(jdbc.queryForList(eq(NotInterestedService.ACTIVE_SQL), eq(Long.class), eq(7L),
                any(OffsetDateTime.class))).willReturn(List.of(1L, 2L));

        assertEquals(List.of(1L, 2L), service.activeContentIds(7L));
        verify(jdbc).queryForList(NotInterestedService.ACTIVE_SQL, Long.class, 7L, NOW_ODT.minusDays(90));
    }
}
