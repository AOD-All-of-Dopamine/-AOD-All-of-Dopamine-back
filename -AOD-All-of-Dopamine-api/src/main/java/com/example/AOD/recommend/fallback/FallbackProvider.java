package com.example.AOD.recommend.fallback;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.WorkFilters;
import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.api.service.WorkApiService;
import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.entity.ExternalRanking;
import com.example.shared.repository.ExternalRankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 추천을 줄 수 없을 때의 대체 목록 (REC_TAB_DESIGN §2-7 · 설계 §10).
 *
 * 1순위: external_ranking 중 content 가 연결되고 성인이 아닌 행.
 * 2순위(마지막 보루): 1순위가 한 장도 안 나오면 그 탭 도메인의 기본 작품 목록 첫 쪽.
 *   로컬 개발 DB 는 external_ranking 중 content 가 연결된 행이 0개라 실제로 이 경로를 탄다.
 *   대체 응답이 비면 화면이 아예 빈다 — 카탈로그에 작품이 있으면 뭐라도 보여 준다.
 *
 * 대체 목록에는 이유 문구를 달지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FallbackProvider {

    /** 전체 탭 번갈아 뽑기 순서 (웹툰은 전체 탭에 들어가지 않는다 — 서빙 README §3). */
    static final List<String> ALL_TAB_PLATFORMS = List.of("Steam", "TMDB_MOVIE", "TMDB_TV", "NaverSeries");

    private static final Map<String, String> PLATFORM_BY_TAB = Map.of(
            "game", "Steam",
            "movie", "TMDB_MOVIE",
            "tv", "TMDB_TV",
            "webtoon", "NaverWebtoon",
            "webnovel", "NaverSeries");

    /** 마지막 보루에서 쓸 도메인. all 은 도메인 구분 없음(null). */
    private static final Map<String, Domain> DOMAIN_BY_TAB = Map.of(
            "game", Domain.GAME,
            "movie", Domain.MOVIE,
            "tv", Domain.TV,
            "webtoon", Domain.WEBTOON,
            "webnovel", Domain.WEBNOVEL);

    private final ExternalRankingRepository externalRankingRepository;
    private final WorkApiService workApiService;

    public List<WorkSummaryDTO> byTab(String tab, int size) {
        List<Content> fromRanking = fromRanking(tab, size);
        if (!fromRanking.isEmpty()) return workApiService.toEnrichedSummaries(fromRanking);

        log.debug("랭킹 대체가 비었다 — 기본 목록으로 간다 (tab={})", tab);
        WorkFilters noFilters = null;
        PageResponse<WorkSummaryDTO> page =
                workApiService.getWorks(DOMAIN_BY_TAB.get(tab), null, noFilters, PageRequest.of(0, size));
        return page == null || page.getContent() == null ? List.of() : page.getContent();
    }

    private List<Content> fromRanking(String tab, int size) {
        if ("all".equals(tab)) return interleave(size);
        String platform = PLATFORM_BY_TAB.get(tab);
        if (platform == null) return List.of();
        List<Content> lane = usable(platform);
        return lane.size() > size ? new ArrayList<>(lane.subList(0, size)) : lane;
    }

    /** 한 플랫폼의 랭킹 순서대로, content 가 있고 성인이 아니며 중복이 아닌 작품. */
    private List<Content> usable(String platform) {
        List<Content> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (ExternalRanking row : externalRankingRepository.findByPlatformWithContent(platform)) {
            Content content = row.getContent();
            // JPQL 은 미매핑 행(c IS NULL)을 일부러 남긴다(랭킹 화면이 크롤 시점 제목을 쓴다) — 추천 대체에서는 못 쓴다.
            if (content == null || Boolean.TRUE.equals(content.getIsAdult())) continue;
            if (seen.add(content.getContentId())) out.add(content);
        }
        return out;
    }

    /** 전체 탭: 플랫폼별 순위 목록을 1위부터 번갈아 뽑는다. */
    private List<Content> interleave(int size) {
        Map<String, List<Content>> lanes = new HashMap<>();
        int longest = 0;
        for (String platform : ALL_TAB_PLATFORMS) {
            List<Content> lane = usable(platform);
            lanes.put(platform, lane);
            longest = Math.max(longest, lane.size());
        }
        List<Content> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < longest && out.size() < size; i++) {
            for (String platform : ALL_TAB_PLATFORMS) {
                if (out.size() >= size) break;
                List<Content> lane = lanes.get(platform);
                if (i >= lane.size()) continue;
                Content content = lane.get(i);
                if (seen.add(content.getContentId())) out.add(content);
            }
        }
        return out;
    }
}
