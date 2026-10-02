package com.example.crawler.contents.game.steam.portrait;

import com.example.crawler.contents.game.steam.portrait.SteamPortraitClient.Portrait;
import com.example.crawler.contents.game.steam.portrait.SteamPortraitClient.SteamPortraitException;
import com.example.crawler.contents.game.steam.portrait.SteamPortraitStore.Checked;
import com.example.crawler.contents.game.steam.portrait.SteamPortraitStore.Target;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 세로 표지 동기화 실행 규칙 (설계 "SteamPortraitSyncService"). */
class SteamPortraitSyncServiceTest {

    private final SteamPortraitStore store = mock(SteamPortraitStore.class);
    private final SteamPortraitClient client = mock(SteamPortraitClient.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final List<Long> sleeps = new ArrayList<>();
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-01T20:10:00Z"));
    private final Clock clock = new Clock() {
        public ZoneOffset getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId z) { return this; }
        public Instant instant() { return now.get(); }
    };
    private final SteamPortraitSyncService service =
            new SteamPortraitSyncService(store, client, registry, clock, sleeps::add);

    private static List<Target> targets(int n) {
        return LongStream.rangeClosed(1, n).mapToObj(i -> new Target(1000 + i, 10 + i)).toList();
    }

    /** 앞의 절반은 표지, 뒤 절반은 없음. 마지막 하나는 응답에 없음. */
    private static Map<Long, Portrait> answers(List<Long> ids) {
        Map<Long, Portrait> out = new HashMap<>();
        for (int i = 0; i < ids.size() - 1; i++) {
            long id = ids.get(i);
            out.put(id, new Portrait(id, i % 2 == 0 ? "https://cdn/" + id + ".jpg" : null));
        }
        return out;
    }

    @Test
    @SuppressWarnings("unchecked")
    void batchesOf200WithPauseAndSavesCheckedOnly() {
        when(store.targets(anyInt())).thenReturn(targets(450));
        when(client.fetch(anyList())).thenAnswer(inv -> answers(inv.getArgument(0)));

        SteamPortraitSyncService.Report report = service.run(3);

        verify(store).targets(3 * 200);
        ArgumentCaptor<List<Long>> asked = ArgumentCaptor.forClass(List.class);
        verify(client, times(3)).fetch(asked.capture());
        assertThat(asked.getAllValues()).extracting(List::size).containsExactly(200, 200, 50);
        assertThat(sleeps).containsExactly(2_000L, 2_000L);           // 묶음 사이에만
        ArgumentCaptor<List<Checked>> saved = ArgumentCaptor.forClass(List.class);
        verify(store, times(3)).save(saved.capture(), any());
        assertThat(saved.getAllValues()).extracting(List::size).containsExactly(199, 199, 49); // 응답에 없는 1개씩 빠짐
        assertThat(saved.getAllValues().get(0).get(0)).isEqualTo(new Checked(1001, "https://cdn/11.jpg"));
        assertThat(saved.getAllValues().get(0).get(1)).isEqualTo(new Checked(1002, null)); // 표지 없음도 확인함
        assertThat(report).isEqualTo(new SteamPortraitSyncService.Report(3, 450, 225, 222, 3, false, false));
        assertThat(registry.get("steam.portrait.items").tag("result", "cover").counter().count()).isEqualTo(225);
        assertThat(registry.get("steam.portrait.last.success").gauge().value()).isEqualTo(now.get().getEpochSecond());
    }

    @Test
    void callFailureStopsRunAndSavesNothingForThatBatch() {
        when(store.targets(anyInt())).thenReturn(targets(400));
        when(client.fetch(anyList()))
                .thenAnswer(inv -> answers(inv.getArgument(0)))
                .thenThrow(new SteamPortraitException("429 Too Many Requests", null));

        SteamPortraitSyncService.Report report = service.run(5);

        verify(client, times(2)).fetch(anyList());
        verify(store, times(1)).save(anyList(), any());               // 첫 묶음만
        assertThat(report.failed()).isTrue();
        assertThat(registry.get("steam.portrait.runs").tag("outcome", "failed").counter().count()).isEqualTo(1);
        assertThat(registry.get("steam.portrait.last.success").gauge().value()).isZero();
    }

    @Test
    void timeLimitStopsBeforeNextBatch() {
        when(store.targets(anyInt())).thenReturn(targets(600));
        when(client.fetch(anyList())).thenAnswer(inv -> {
            now.set(now.get().plusSeconds(16 * 60));                  // 첫 호출이 16분 걸렸다
            return answers(inv.getArgument(0));
        });

        SteamPortraitSyncService.Report report = service.run(3);

        verify(client, times(1)).fetch(anyList());
        assertThat(report.timedOut()).isTrue();
        assertThat(report.failed()).isFalse();
    }

    @Test
    void nothingToDo() {
        when(store.targets(anyInt())).thenReturn(List.of());
        SteamPortraitSyncService.Report report = service.run(40);
        verify(client, never()).fetch(anyList());
        assertThat(report.asked()).isZero();
    }

    @Test
    void singleRunLockAndBounds() throws Exception {
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(store.targets(anyInt())).thenAnswer(inv -> {
            inside.countDown();
            release.await(5, TimeUnit.SECONDS);
            return List.of();
        });

        assertThat(service.start(5_000)).isTrue();                    // 상한 1,000 으로 잘린다
        assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(service.isRunning()).isTrue();
        assertThat(service.start(10)).isFalse();                      // 돌고 있으면 건너뜀
        release.countDown();
        for (int i = 0; i < 50 && service.isRunning(); i++) Thread.sleep(20);
        assertThat(service.isRunning()).isFalse();
        verify(store).targets(1_000 * 200);
        assertThat(service.start(0)).isTrue();                        // 하한 1
    }
}
