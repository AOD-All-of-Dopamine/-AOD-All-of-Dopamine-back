package com.example.AOD.api.featured;

import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.api.featured.FeaturedPickStore.FeaturedPick;
import com.example.AOD.api.service.WorkApiService;
import com.example.shared.entity.Content;
import com.example.shared.entity.ExternalRanking;
import com.example.shared.repository.ContentRepository;
import com.example.shared.repository.ExternalRankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 오늘의 작품 선정 규칙 (설계: 프론트 docs/superpowers/specs/2026-09-26-home-featured-today-design.md). */
class FeaturedWorkServiceTest {

    /** epochDay % 3 == 0 → 게임 날. */
    private static final LocalDate GAME_DAY = LocalDate.of(2026, 9, 25);
    private static final LocalDate MOVIE_DAY = GAME_DAY.plusDays(1);
    private static final LocalDate TV_DAY = GAME_DAY.plusDays(2);

    private ExternalRankingRepository rankings;
    private ContentRepository contents;
    private WorkApiService workApiService;
    private FeaturedPickStore store;
    private FeaturedWorkService service;
    /** 가짜 저장소 — insert-if-absent. */
    private final AtomicReference<FeaturedPick> saved = new AtomicReference<>();
    private Set<Long> recent = Set.of();
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        rankings = mock(ExternalRankingRepository.class);
        contents = mock(ContentRepository.class);
        workApiService = mock(WorkApiService.class);
        store = mock(FeaturedPickStore.class);
        given(store.find(any())).willAnswer(inv -> Optional.ofNullable(saved.get()));
        org.mockito.Mockito.doAnswer(inv -> { saved.compareAndSet(null, inv.getArgument(0)); return null; })
                .when(store).insertIfAbsent(any());
        given(store.recentContentIds(any(), anyInt())).willAnswer(inv -> recent);
        given(rankings.findByPlatformWithContent(anyString())).willReturn(List.of());
        given(workApiService.toEnrichedSummaries(anyList())).willAnswer(inv -> {
            List<Content> cs = inv.getArgument(0);
            List<WorkSummaryDTO> out = new ArrayList<>();
            for (Content c : cs) {
                WorkSummaryDTO dto = new WorkSummaryDTO();
                dto.setId(c.getContentId());
                dto.setTitle(c.getMasterTitle());
                out.add(dto);
            }
            return out;
        });
        service = new FeaturedWorkService(rankings, contents, workApiService, store,
                Clock.fixed(Instant.parse("2026-09-25T03:00:00Z"), ZoneOffset.UTC));
    }

    private Content content(String title) {
        Content c = new Content();
        c.setContentId(nextId++);
        c.setMasterTitle(title);
        c.setPosterImageUrl("http://img/" + title + ".jpg");
        given(contents.findById(c.getContentId())).willReturn(Optional.of(c));
        return c;
    }

    private ExternalRanking row(String platform, int rank, Content content, Double score, Integer count, String label) {
        ExternalRanking r = new ExternalRanking();
        r.setPlatform(platform);
        r.setRanking(rank);
        r.setTitle(content != null ? content.getMasterTitle() : "unmapped-" + rank);
        r.setContent(content);
        r.setRatingScore(score);
        r.setRatingCount(count);
        r.setRatingLabel(label);
        r.setFetchedAt(Instant.parse("2026-09-24T19:04:00Z"));
        return r;
    }

    private ExternalRanking steam(int rank, String title, double positive, int reviews) {
        return row("Steam", rank, content(title), positive, reviews, "Very Positive");
    }

    private ExternalRanking tmdb(String platform, int rank, String title, double rating, int votes) {
        return row(platform, rank, content(title), rating, votes, null);
    }

    private void ranking(String platform, ExternalRanking... rows) {
        given(rankings.findByPlatformWithContent(platform)).willReturn(List.of(rows));
    }

    // ---------- 날짜 ----------

    @Test
    void dateSwitchesAt0500Kst() {
        // 2026-09-26 04:59 KST = 09-25 19:59Z → 아직 09-25
        assertThat(FeaturedWorkService.featuredDate(Instant.parse("2026-09-25T19:59:59Z"))).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(FeaturedWorkService.featuredDate(Instant.parse("2026-09-25T20:00:00Z"))).isEqualTo(LocalDate.of(2026, 9, 26));
        // 자정 KST 는 아직 전날
        assertThat(FeaturedWorkService.featuredDate(Instant.parse("2026-09-25T15:00:00Z"))).isEqualTo(LocalDate.of(2026, 9, 25));
    }

    @Test
    void cacheLifetimeRunsToNextSwitch() {
        // 09-25 12:00 KST → 09-26 05:00 KST = 17시간
        Instant noonKst = Instant.parse("2026-09-25T03:00:00Z");
        assertThat(FeaturedWorkService.secondsUntilNextSwitch(LocalDate.of(2026, 9, 25), noonKst)).isEqualTo(17 * 3600);
        // 바뀌기 직전에도 최소 60초
        assertThat(FeaturedWorkService.secondsUntilNextSwitch(LocalDate.of(2026, 9, 25), Instant.parse("2026-09-25T19:59:59Z"))).isEqualTo(60);
        assertThat(service.today()).isEqualTo(LocalDate.of(2026, 9, 25));
    }

    // ---------- 분야 순환 ----------

    @Test
    void platformRotatesByEpochDay() {
        ranking("Steam", steam(1, "게임", 0.95, 50_000));
        ranking("TMDB_MOVIE", tmdb("TMDB_MOVIE", 1, "영화", 8.0, 5_000));
        ranking("TMDB_TV", tmdb("TMDB_TV", 1, "시리즈", 8.5, 5_000));

        assertThat(service.choose(GAME_DAY).orElseThrow().platform()).isEqualTo("Steam");
        assertThat(service.choose(MOVIE_DAY).orElseThrow().platform()).isEqualTo("TMDB_MOVIE");
        assertThat(service.choose(TV_DAY).orElseThrow().platform()).isEqualTo("TMDB_TV");
        assertThat(service.choose(GAME_DAY.plusDays(3)).orElseThrow().platform()).isEqualTo("Steam");
    }

    @Test
    void emptyPlatformFallsThroughToNext() {
        // 게임 날인데 게임 후보가 문턱 미달 → 영화
        ranking("Steam", steam(1, "게임", 0.70, 50_000));
        ranking("TMDB_MOVIE", tmdb("TMDB_MOVIE", 4, "영화", 8.0, 5_000));

        FeaturedPick pick = service.choose(GAME_DAY).orElseThrow();
        assertThat(pick.platform()).isEqualTo("TMDB_MOVIE");
        assertThat(pick.ranking()).isEqualTo(4);
        assertThat(pick.basis()).isEqualTo("tmdb");
    }

    @Test
    void tvDayWrapsAroundToGame() {
        ranking("Steam", steam(2, "게임", 0.95, 50_000));
        FeaturedPick pick = service.choose(TV_DAY).orElseThrow();
        assertThat(pick.platform()).isEqualTo("Steam");
        assertThat(pick.basis()).isEqualTo("steam");
    }

    // ---------- 후보 ----------

    @Test
    void picksTopRankedAmongPassing() {
        ranking("Steam",
                steam(1, "미달", 0.80, 50_000),
                steam(3, "통과3", 0.93, 20_000),
                steam(5, "통과5", 0.99, 900_000));
        FeaturedPick pick = service.choose(GAME_DAY).orElseThrow();
        assertThat(pick.ranking()).isEqualTo(3);
        assertThat(pick.ratingScore()).isEqualTo(0.93);
        assertThat(pick.ratingCount()).isEqualTo(20_000);
        assertThat(pick.ratingLabel()).isEqualTo("Very Positive");
    }

    @Test
    void rank30IsInAnd31IsOut() {
        ranking("Steam", steam(31, "31위", 0.95, 50_000));
        assertThat(service.choose(GAME_DAY)).isEmpty();
        ranking("Steam", steam(31, "31위", 0.95, 50_000), steam(30, "30위", 0.95, 50_000));
        assertThat(service.choose(GAME_DAY).orElseThrow().ranking()).isEqualTo(30);
    }

    @Test
    void steamGateUsesRawRatio() {
        assertThat(FeaturedWorkService.passesGate(row("Steam", 1, null, 0.899, 50_000, "Very Positive"), "Steam")).isFalse();
        assertThat(FeaturedWorkService.passesGate(row("Steam", 1, null, 0.900, 50_000, "Very Positive"), "Steam")).isTrue();
        assertThat(FeaturedWorkService.passesGate(row("Steam", 1, null, 0.95, 9_999, "Very Positive"), "Steam")).isFalse();
        assertThat(FeaturedWorkService.passesGate(row("Steam", 1, null, 0.95, 10_000, "Very Positive"), "Steam")).isTrue();
        assertThat(FeaturedWorkService.passesGate(row("Steam", 1, null, 0.95, 50_000, "Overwhelmingly Positive"), "Steam")).isTrue();
        // 판정이 Positive 가 아니면 비율이 높아도 탈락
        assertThat(FeaturedWorkService.passesGate(row("Steam", 1, null, 0.95, 50_000, "Mixed"), "Steam")).isFalse();
        assertThat(FeaturedWorkService.passesGate(row("Steam", 1, null, 0.95, 50_000, null), "Steam")).isFalse();
        // 신선한 값이 없으면(배포 직후) 탈락
        assertThat(FeaturedWorkService.passesGate(row("Steam", 1, null, null, null, null), "Steam")).isFalse();
    }

    @Test
    void tmdbGate() {
        assertThat(FeaturedWorkService.passesGate(row("TMDB_MOVIE", 1, null, 7.49, 5_000, null), "TMDB_MOVIE")).isFalse();
        assertThat(FeaturedWorkService.passesGate(row("TMDB_MOVIE", 1, null, 7.5, 5_000, null), "TMDB_MOVIE")).isTrue();
        assertThat(FeaturedWorkService.passesGate(row("TMDB_TV", 1, null, 8.0, 499, null), "TMDB_TV")).isFalse();
        assertThat(FeaturedWorkService.passesGate(row("TMDB_TV", 1, null, 8.0, 500, null), "TMDB_TV")).isTrue();
    }

    @Test
    void skipsUnmappedAdultAndNoThumbnail() {
        Content adult = content("성인");
        adult.setIsAdult(true);
        Content noThumb = content("썸네일없음");
        noThumb.setPosterImageUrl(" ");
        ranking("Steam",
                row("Steam", 1, null, 0.95, 50_000, "Very Positive"),
                row("Steam", 2, adult, 0.95, 50_000, "Very Positive"),
                row("Steam", 3, noThumb, 0.95, 50_000, "Very Positive"),
                steam(4, "정상", 0.95, 50_000));
        assertThat(service.choose(GAME_DAY).orElseThrow().ranking()).isEqualTo(4);
    }

    @Test
    void recentPicksAreExcludedFor45Days() {
        ExternalRanking first = steam(1, "어제 작품", 0.95, 50_000);
        ranking("Steam", first, steam(2, "다른 작품", 0.95, 50_000));
        recent = Set.of(first.getContent().getContentId());

        assertThat(service.choose(GAME_DAY).orElseThrow().ranking()).isEqualTo(2);
        verify(store).recentContentIds(GAME_DAY, 45);
    }

    // ---------- 저장 · 204 ----------

    @Test
    void pickStoresAndReturnsDto() {
        ranking("Steam", steam(8, "Hades II", 0.9431, 125_310));

        FeaturedWorkDTO dto = service.pick(GAME_DAY).orElseThrow();
        assertThat(dto.date()).isEqualTo("2026-09-25");
        assertThat(dto.work().getTitle()).isEqualTo("Hades II");
        assertThat(dto.reason().platform()).isEqualTo("Steam");
        assertThat(dto.reason().ranking()).isEqualTo(8);
        assertThat(dto.reason().ratingScore()).isEqualTo(0.9431);
        assertThat(saved.get()).isNotNull();
    }

    @Test
    void alreadyStoredDayIsReturnedAsIs() {
        Content stored = content("저장된 작품");
        saved.set(new FeaturedPick(GAME_DAY, stored.getContentId(), "TMDB_TV", 12, "tmdb", 8.1, 900, null));
        ranking("Steam", steam(1, "오늘 1위", 0.99, 900_000));

        FeaturedWorkDTO dto = service.pick(GAME_DAY).orElseThrow();
        assertThat(dto.work().getTitle()).isEqualTo("저장된 작품");
        assertThat(dto.reason().platform()).isEqualTo("TMDB_TV");
        verify(rankings, never()).findByPlatformWithContent(anyString());
        verify(store, never()).insertIfAbsent(any());
    }

    @Test
    void concurrentWriterWins() {
        // 고르는 사이 다른 인스턴스가 먼저 저장 → 저장된 쪽을 돌려준다
        ranking("Steam", steam(1, "내가 고른 작품", 0.95, 50_000));
        Content other = content("먼저 저장된 작품");
        given(store.find(any())).willReturn(Optional.empty())
                .willReturn(Optional.of(new FeaturedPick(GAME_DAY, other.getContentId(), "Steam", 2, "steam", 0.97, 80_000, "Very Positive")));

        assertThat(service.pick(GAME_DAY).orElseThrow().work().getTitle()).isEqualTo("먼저 저장된 작품");
    }

    @Test
    void nothingAnywhereIs204AndNotStored() {
        ranking("Steam", steam(1, "미달", 0.5, 50_000));
        assertThat(service.pick(GAME_DAY)).isEmpty();
        verify(store, never()).insertIfAbsent(any());
    }

    @Test
    void storedButNowAdultIsHidden() {
        Content c = content("나중에 성인 판정");
        c.setIsAdult(true);
        saved.set(new FeaturedPick(GAME_DAY, c.getContentId(), "Steam", 1, "steam", 0.95, 50_000, "Very Positive"));
        assertThat(service.pick(GAME_DAY)).isEmpty();
        verify(contents).findById(eq(c.getContentId()));
        verify(workApiService, never()).toEnrichedSummaries(anyList());
    }

    @Test
    void storedButDeletedIsHidden() {
        saved.set(new FeaturedPick(GAME_DAY, 999L, "Steam", 1, "steam", 0.95, 50_000, "Very Positive"));
        given(contents.findById(anyLong())).willReturn(Optional.empty());
        assertThat(service.pick(GAME_DAY)).isEmpty();
    }
}
