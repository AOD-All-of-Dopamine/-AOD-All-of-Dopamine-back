package com.example.AOD.api.featured;

import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.api.featured.FeaturedPickStore.FeaturedPick;
import com.example.AOD.api.service.WorkApiService;
import com.example.shared.entity.Content;
import com.example.shared.entity.ExternalRanking;
import com.example.shared.repository.ContentRepository;
import com.example.shared.repository.ExternalRankingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 홈 "오늘의 작품" — 지금 인기 있고 평가가 좋은 작품을 하루 한 작품
 * (설계: 프론트 docs/superpowers/specs/2026-09-26-home-featured-today-design.md "선정 규칙").
 *
 * <ol>
 *   <li>오늘 = KST 날짜, 05:00 에 넘어간다(랭킹이 04:04~ 에 갱신된 뒤)</li>
 *   <li>그날 featured_pick 행이 있으면 그대로 — 없을 때만 고르고 저장(insert-if-absent)</li>
 *   <li>분야 = epochDay % 3 → 게임 · 영화 · 시리즈, 비면 다음 분야</li>
 *   <li>후보 = 랭킹 30위 이내 · 연결됨 · 성인 아님 · 썸네일 있음 · 고평가(랭킹을 받을 때의 신선한 값) · 최근 45일 미선정</li>
 *   <li>고르기 = 랭킹 최상위</li>
 * </ol>
 */
@Slf4j
@Service
public class FeaturedWorkService {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 날짜가 넘어가는 시각 — 랭킹 수집(04:00~ 시작) 뒤. */
    public static final int SWITCH_HOUR = 5;
    public static final int MAX_RANKING = 30;
    public static final int REPEAT_BLOCK_DAYS = 45;

    public static final double GAME_MIN_POSITIVE = 0.90;
    public static final int GAME_MIN_REVIEWS = 10_000;
    public static final double TMDB_MIN_RATING = 7.5;
    public static final int TMDB_MIN_VOTES = 500;

    /** 분야 순환 순서 — epochDay % 3 번째부터. 웹툰 · 웹소설은 평점 신호가 없어 넣지 않는다. */
    static final List<String> PLATFORM_CYCLE = List.of("Steam", "TMDB_MOVIE", "TMDB_TV");

    private final ExternalRankingRepository rankingRepository;
    private final ContentRepository contentRepository;
    private final WorkApiService workApiService;
    private final FeaturedPickStore store;
    private final Clock clock;

    @Autowired
    public FeaturedWorkService(ExternalRankingRepository rankingRepository, ContentRepository contentRepository,
                               WorkApiService workApiService, FeaturedPickStore store) {
        this(rankingRepository, contentRepository, workApiService, store, Clock.systemUTC());
    }

    FeaturedWorkService(ExternalRankingRepository rankingRepository, ContentRepository contentRepository,
                        WorkApiService workApiService, FeaturedPickStore store, Clock clock) {
        this.rankingRepository = rankingRepository;
        this.contentRepository = contentRepository;
        this.workApiService = workApiService;
        this.store = store;
        this.clock = clock;
    }

    /** 05:00 KST 에 넘어가는 날짜. 존을 명시한다 — JVM 기본 시간대에 기대지 않는다. */
    public static LocalDate featuredDate(Instant now) {
        return now.atZone(KST).minusHours(SWITCH_HOUR).toLocalDate();
    }

    /** 다음 날짜가 시작되는 시각(다음 05:00 KST). */
    public static Instant nextSwitch(LocalDate date) {
        return date.plusDays(1).atTime(SWITCH_HOUR, 0).atZone(KST).toInstant();
    }

    public LocalDate today() {
        return featuredDate(clock.instant());
    }

    public Instant now() {
        return clock.instant();
    }

    @Transactional
    public Optional<FeaturedWorkDTO> pick(LocalDate date) {
        Optional<FeaturedPick> saved = store.find(date);
        if (saved.isEmpty()) {
            Optional<FeaturedPick> chosen = choose(date);
            if (chosen.isEmpty()) {
                log.warn("오늘의 작품 없음 date={} — 세 분야 모두 후보가 없다", date);
                return Optional.empty();
            }
            store.insertIfAbsent(chosen.get());
            saved = store.find(date); // 동시에 다른 요청이 먼저 저장했으면 그 작품
        }
        return saved.flatMap(this::toDto);
    }

    Optional<FeaturedPick> choose(LocalDate date) {
        Set<Long> recent = store.recentContentIds(date, REPEAT_BLOCK_DAYS);
        int start = (int) Math.floorMod(date.toEpochDay(), PLATFORM_CYCLE.size());
        Instant staleBefore = date.atTime(4, 0).atZone(KST).toInstant();
        for (int step = 0; step < PLATFORM_CYCLE.size(); step++) {
            String platform = PLATFORM_CYCLE.get((start + step) % PLATFORM_CYCLE.size());
            List<ExternalRanking> rows = rankingRepository.findByPlatformWithContent(platform);
            List<ExternalRanking> candidates = candidates(rows, platform, recent);
            if (!rows.isEmpty() && rows.stream().allMatch(r -> r.getFetchedAt() == null || r.getFetchedAt().isBefore(staleBefore))) {
                log.warn("오늘의 작품 — {} 랭킹이 오늘 04:00 이전 한 벌이다(크롤 지연?) date={}", platform, date);
            }
            if (candidates.isEmpty()) {
                log.warn("오늘의 작품 — {} 후보 없음, 다음 분야로 date={}", platform, date);
                continue;
            }
            ExternalRanking top = candidates.get(0);
            FeaturedPick pick = new FeaturedPick(date, top.getContent().getContentId(), platform, top.getRanking(),
                    "Steam".equals(platform) ? "steam" : "tmdb",
                    top.getRatingScore(), top.getRatingCount(), top.getRatingLabel());
            log.info("오늘의 작품 date={} platform={} ranking={} contentId={} title=\"{}\" score={} count={} label={} candidates={}{}",
                    date, platform, top.getRanking(), pick.contentId(), top.getTitle(), top.getRatingScore(),
                    top.getRatingCount(), top.getRatingLabel(), candidates.size(), step > 0 ? " (분야 넘김 " + step + ")" : "");
            return Optional.of(pick);
        }
        return Optional.empty();
    }

    /** 랭킹 순서 그대로(쿼리가 정렬 · 매핑된 성인 제외) 문턱을 넘는 행. */
    static List<ExternalRanking> candidates(List<ExternalRanking> rows, String platform, Set<Long> recent) {
        List<ExternalRanking> out = new ArrayList<>();
        Set<Long> seen = new java.util.HashSet<>();
        for (ExternalRanking row : rows) {
            Content content = row.getContent();
            if (row.getRanking() == null || row.getRanking() > MAX_RANKING) continue;
            if (content == null || Boolean.TRUE.equals(content.getIsAdult())) continue;
            if (content.getPosterImageUrl() == null || content.getPosterImageUrl().isBlank()) continue;
            if (!seen.add(content.getContentId()) || recent.contains(content.getContentId())) continue;
            if (!passesGate(row, platform)) continue;
            out.add(row);
        }
        return out;
    }

    /** 고평가 문턱 — 반올림 전 원자료로 판정한다. */
    static boolean passesGate(ExternalRanking row, String platform) {
        Double score = row.getRatingScore();
        Integer count = row.getRatingCount();
        if (score == null || count == null) return false;
        if ("Steam".equals(platform)) {
            String label = row.getRatingLabel();
            return label != null && label.endsWith("Positive") && score >= GAME_MIN_POSITIVE && count >= GAME_MIN_REVIEWS;
        }
        return score >= TMDB_MIN_RATING && count >= TMDB_MIN_VOTES;
    }

    private Optional<FeaturedWorkDTO> toDto(FeaturedPick pick) {
        Optional<Content> content = contentRepository.findById(pick.contentId());
        if (content.isEmpty() || Boolean.TRUE.equals(content.get().getIsAdult())) {
            log.warn("오늘의 작품 date={} contentId={} 를 보여 줄 수 없다(삭제 · 성인)", pick.date(), pick.contentId());
            return Optional.empty();
        }
        List<WorkSummaryDTO> works = workApiService.toEnrichedSummaries(List.of(content.get()));
        if (works.isEmpty()) return Optional.empty();
        return Optional.of(new FeaturedWorkDTO(pick.date().toString(), works.get(0),
                new FeaturedWorkDTO.Reason(pick.platform(), pick.ranking(), pick.basis(),
                        pick.ratingScore(), pick.ratingCount(), pick.ratingLabel())));
    }

    /** 응답 캐시 수명 — 다음 05:00 KST 까지(최소 60초). */
    public static long secondsUntilNextSwitch(LocalDate date, Instant now) {
        long seconds = java.time.Duration.between(now, nextSwitch(date)).getSeconds();
        return Math.max(60, seconds);
    }
}
