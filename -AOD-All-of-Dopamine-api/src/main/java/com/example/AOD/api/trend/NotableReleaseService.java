package com.example.AOD.api.trend;

import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.api.service.WorkApiService;
import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.entity.ExternalRanking;
import com.example.shared.repository.ContentRepository;
import com.example.shared.repository.ExternalRankingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 트렌드 "새로 나온 주목작" — 분야마다 {@value #PER_DOMAIN}편 (설계 2026-10-04-trend-explore-design.md v2 결정 1).
 *
 * <ol>
 *   <li><b>오늘 순위에 오른 최근작</b>을 순위 순(분야별 창: 영화 · 시리즈 60일, 게임 30일, 웹툰 45일, 웹소설 14일)</li>
 *   <li>모자라면 — 영화 · 시리즈: 최근 개봉 중 TMDB 투표 많은 순 / 게임: 리뷰 하한 통과분 리뷰 많은 순 /
 *       웹툰 · 웹소설: 최신순</li>
 * </ol>
 * 게임 리뷰 수 · TMDB 투표 수는 수집 때 굳어 신작에서 작으므로 순위를 먼저 본다.
 * <p>결과는 서버에서 {@link #CACHE_TTL} 동안(같은 KST 날짜 안) 들고 있는다 — 한 번에 쿼리 수십 개라 트렌드 탭마다
 * 다시 고르지 않게. 앱 캐시 매니저(ConcurrentMapCacheManager)는 만료가 없어 쓰지 않는다.</p>
 */
@Service
public class NotableReleaseService {

    public static final int PER_DOMAIN = 2;
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final List<Domain> ORDER = List.of(Domain.MOVIE, Domain.TV, Domain.GAME, Domain.WEBTOON, Domain.WEBNOVEL);
    static final Map<Domain, String> PLATFORM = Map.of(Domain.MOVIE, "TMDB_MOVIE", Domain.TV, "TMDB_TV",
            Domain.GAME, "Steam", Domain.WEBTOON, "NaverWebtoon", Domain.WEBNOVEL, "NaverSeries");
    static final Map<Domain, Integer> WINDOW_DAYS = Map.of(Domain.MOVIE, 60, Domain.TV, 60, Domain.GAME, 30,
            Domain.WEBTOON, 45, Domain.WEBNOVEL, 14);
    /** 채우기 후보 상한(분야당) — 메모리에서 투표 · 리뷰 순으로 고른다 */
    static final int FILL_CANDIDATES = 100;

    static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private record Snapshot(LocalDate day, Instant at, List<Group> groups) { }

    private volatile Snapshot cached;

    private final ExternalRankingRepository rankingRepository;
    private final ContentRepository contentRepository;
    private final WorkApiService workApiService;
    private final Clock clock;

    @Autowired
    public NotableReleaseService(ExternalRankingRepository rankingRepository, ContentRepository contentRepository,
                                 WorkApiService workApiService) {
        this(rankingRepository, contentRepository, workApiService, Clock.systemUTC());
    }

    NotableReleaseService(ExternalRankingRepository rankingRepository, ContentRepository contentRepository,
                          WorkApiService workApiService, Clock clock) {
        this.rankingRepository = rankingRepository;
        this.contentRepository = contentRepository;
        this.workApiService = workApiService;
        this.clock = clock;
    }

    /** 이유 — type: RANK(순위 · platform) · VOTES(TMDB 투표 수) · REVIEWS(Steam 리뷰 수) · LATEST(시작일). */
    public record Reason(String type, Integer value, String platform, String date) { }

    public record Item(WorkSummaryDTO work, Reason reason) { }

    public record Group(String domain, List<Item> items) { }

    @Transactional(readOnly = true)
    public List<Group> notable() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, KST);
        Snapshot hit = cached;
        if (hit != null && hit.day().equals(today) && now.isBefore(hit.at().plus(CACHE_TTL))) return hit.groups();
        List<Group> out = new ArrayList<>();
        for (Domain domain : ORDER) out.add(new Group(domain.name(), pick(domain, today)));
        List<Group> groups = List.copyOf(out);
        cached = new Snapshot(today, now, groups);
        return groups;
    }

    List<Item> pick(Domain domain, LocalDate today) {
        LocalDate from = today.minusDays(WINDOW_DAYS.get(domain));
        String platform = PLATFORM.get(domain);
        Map<Long, Reason> reasons = new LinkedHashMap<>();
        Map<Long, Content> contents = new HashMap<>();

        // 1. 오늘 순위에 오른 최근작 (쿼리가 순위 순 · 연결된 성인 제외)
        for (ExternalRanking row : rankingRepository.findByPlatformWithContent(platform)) {
            if (reasons.size() >= PER_DOMAIN) break;
            Content c = row.getContent();
            if (c == null || Boolean.TRUE.equals(c.getIsAdult()) || c.getReleaseDate() == null) continue;
            if (c.getReleaseDate().isBefore(from) || c.getReleaseDate().isAfter(today)) continue;
            if (reasons.containsKey(c.getContentId())) continue;
            reasons.put(c.getContentId(), new Reason("RANK", row.getRanking(), platform, null));
            contents.put(c.getContentId(), c);
        }

        List<Item> items = new ArrayList<>();
        if (!contents.isEmpty()) {
            List<Content> ordered = reasons.keySet().stream().map(contents::get).toList();
            for (WorkSummaryDTO w : workApiService.toEnrichedSummaries(ordered)) items.add(new Item(w, reasons.get(w.getId())));
        }
        if (items.size() >= PER_DOMAIN) return items;

        // 2. 채우기 — 분야마다 다른 근거
        Set<Long> taken = new HashSet<>(reasons.keySet());
        List<Content> candidates = contentRepository.findReleasesInDateRange(domain, from, today,
                WorkApiService.RECENT_RELEASE_GAME_MIN_REVIEWS, PageRequest.of(0, FILL_CANDIDATES)).getContent()
                .stream().filter(c -> !taken.contains(c.getContentId())).toList();
        if (candidates.isEmpty()) return items;
        List<WorkSummaryDTO> summaries = new ArrayList<>(workApiService.toEnrichedSummaries(candidates));
        switch (domain) {
            case MOVIE, TV -> summaries.sort(Comparator.comparing(
                    (WorkSummaryDTO w) -> w.getExternalVoteCount() == null ? 0 : w.getExternalVoteCount()).reversed());
            case GAME -> summaries.sort(Comparator.comparing(
                    (WorkSummaryDTO w) -> w.getSteamReviewCount() == null ? 0 : w.getSteamReviewCount()).reversed());
            default -> { } // 최신순 그대로
        }
        for (WorkSummaryDTO w : summaries) {
            if (items.size() >= PER_DOMAIN) break;
            items.add(new Item(w, fillReason(domain, w)));
        }
        return items;
    }

    static Reason fillReason(Domain domain, WorkSummaryDTO w) {
        return switch (domain) {
            case MOVIE, TV -> w.getExternalVoteCount() != null && w.getExternalVoteCount() > 0
                    ? new Reason("VOTES", w.getExternalVoteCount(), null, null)
                    : new Reason("LATEST", null, null, w.getReleaseDate());
            case GAME -> w.getSteamReviewCount() != null && w.getSteamReviewCount() > 0
                    ? new Reason("REVIEWS", w.getSteamReviewCount(), null, null)
                    : new Reason("LATEST", null, null, w.getReleaseDate());
            default -> new Reason("LATEST", null, null, w.getReleaseDate());
        };
    }
}
