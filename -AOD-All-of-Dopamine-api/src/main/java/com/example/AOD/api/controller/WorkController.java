package com.example.AOD.api.controller;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.WorkFilters;
import com.example.AOD.api.dto.WorkResponseDTO;
import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.api.featured.FeaturedWorkDTO;
import com.example.AOD.api.featured.FeaturedWorkService;
import com.example.AOD.api.service.WorkApiService;
import com.example.shared.entity.Domain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/works")
@RequiredArgsConstructor
public class WorkController {

    private final WorkApiService workApiService;
    private final FeaturedWorkService featuredWorkService;

    /**
     * 홈 "오늘의 작품" — 하루 한 작품(05:00 KST 에 바뀜). 200 은 다음 05:00 까지 캐시, 보여 줄 작품이 없으면 204.
     * GET /api/works/featured-today
     */
    @GetMapping("/featured-today")
    public ResponseEntity<FeaturedWorkDTO> getFeaturedToday() {
        java.time.Instant now = featuredWorkService.now();
        java.time.LocalDate date = FeaturedWorkService.featuredDate(now);
        long maxAge = FeaturedWorkService.secondsUntilNextSwitch(date, now);
        CacheControl cache = CacheControl.maxAge(java.time.Duration.ofSeconds(maxAge)).cachePublic();
        return featuredWorkService.pick(date)
                .map(dto -> ResponseEntity.ok().cacheControl(cache).body(dto))
                // 204 는 저장하지 않으니(다음 요청이 다시 고른다) 캐시하지 않는다
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    }

    /**
     * 작품 목록 조회
     * GET /api/works?domain=GAME&keyword=검색어&platforms=steam,epic&genres=액션,RPG&page=0&size=20&sort=masterTitle,asc
     */
    @GetMapping
    public ResponseEntity<PageResponse<WorkSummaryDTO>> getWorks(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) java.util.List<String> platforms,
            @RequestParam(required = false) java.util.List<String> genres,
            @RequestParam(required = false) String releaseFrom,
            @RequestParam(required = false) String releaseTo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) java.util.List<String> weekdays,
            @RequestParam(required = false) java.util.List<String> ageRatings,
            // 게임 도메인 축(리뷰 총수 하한) — 게임 외 도메인과 함께 보내면 0건 (게임 탭 한정 전송 계약)
            @RequestParam(required = false) Integer reviewCountMin,
            // ⚠ A/B 측정용 임시 (troubleshooting/07 §8-1): impl=legacy → 구 WORKS_FILTER 쿼리 경로. 측정 후 제거
            @RequestParam(required = false) String impl,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "masterTitle") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection
    ) {
        Domain domainEnum = null;
        if (domain != null && !domain.isBlank()) {
            try {
                domainEnum = Domain.valueOf(domain.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid domain parameter: {}", domain);
            }
        }

        // 날짜 파라미터는 yyyy-MM-dd만 허용 (native 쿼리 CAST 오류 방지)
        if (isInvalidDate(releaseFrom) || isInvalidDate(releaseTo)) {
            return ResponseEntity.badRequest().build();
        }

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy)
                        .and(Sort.by(Sort.Direction.ASC, "contentId"));
        Pageable pageable = PageRequest.of(page, size, sort);

        WorkFilters filters = new WorkFilters(genres, platforms, releaseFrom, releaseTo, status, weekdays, ageRatings, reviewCountMin);
        PageResponse<WorkSummaryDTO> response = "legacy".equalsIgnoreCase(impl)
                ? workApiService.getWorksLegacy(domainEnum, keyword, filters, pageable)
                : workApiService.getWorks(domainEnum, keyword, filters, pageable);
        return ResponseEntity.ok(response);
    }

    private static boolean isInvalidDate(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            java.time.LocalDate.parse(value);
            return false;
        } catch (java.time.format.DateTimeParseException e) {
            return true;
        }
    }

    /**
     * 작품 상세 조회
     * GET /api/works/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<WorkResponseDTO> getWorkDetail(@PathVariable Long id) {
        WorkResponseDTO response = workApiService.getWorkDetail(id);
        return ResponseEntity.ok(response);
    }

    /**
     * 최근 출시작 조회 (신작)
     * GET /api/releases/recent?domain=GAME&platforms=steam,epic&page=0&size=20
     */
    @GetMapping("/releases/recent")
    public ResponseEntity<PageResponse<WorkSummaryDTO>> getRecentReleases(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) java.util.List<String> platforms,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Domain domainEnum = null;
        if (domain != null && !domain.isBlank()) {
            try {
                domainEnum = Domain.valueOf(domain.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid domain parameter: {}", domain);
            }
        }

        Pageable pageable = PageRequest.of(page, size);
        PageResponse<WorkSummaryDTO> response = workApiService.getRecentReleases(domainEnum, platforms, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * [✨ 신규 기능] 최근 리뷰가 달린 작품 조회
     * GET /api/works/recent-reviews?domain=GAME&platforms=steam,epic&page=0&size=20
     */
    @GetMapping("/recent-reviews")
    public ResponseEntity<PageResponse<WorkSummaryDTO>> getRecentReviewedWorks(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) java.util.List<String> platforms,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Domain domainEnum = null;
        if (domain != null && !domain.isBlank()) {
            try {
                domainEnum = Domain.valueOf(domain.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid domain parameter: {}", domain);
            }
        }

        Pageable pageable = PageRequest.of(page, size);
        PageResponse<WorkSummaryDTO> response = workApiService.getRecentReviewedWorks(domainEnum, platforms, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * 출시 예정작 조회
     * GET /api/releases/upcoming?domain=GAME&platforms=steam,epic&page=0&size=20
     */
    @GetMapping("/releases/upcoming")
    public ResponseEntity<PageResponse<WorkSummaryDTO>> getUpcomingReleases(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) java.util.List<String> platforms,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Domain domainEnum = null;
        if (domain != null && !domain.isBlank()) {
            try {
                domainEnum = Domain.valueOf(domain.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid domain parameter: {}", domain);
            }
        }

        Pageable pageable = PageRequest.of(page, size);
        PageResponse<WorkSummaryDTO> response = workApiService.getUpcomingReleases(domainEnum, platforms, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * 도메인별 사용 가능한 장르 목록 조회 (작품 수 기준 정렬)
     * GET /api/works/genres?domain=GAME
     */
    @GetMapping("/genres")
    public ResponseEntity<java.util.List<String>> getGenres(
            @RequestParam(required = false) String domain
    ) {
        Domain domainEnum = null;
        if (domain != null && !domain.isBlank()) {
            try {
                domainEnum = Domain.valueOf(domain.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid domain parameter: {}", domain);
                return ResponseEntity.badRequest().build();
            }
        }

        java.util.List<String> genres = workApiService.getAvailableGenres(domainEnum);
        return ResponseEntity.ok(genres);
    }

    /**
     * 도메인별 장르별 작품 수 조회 (작품 수 기준 내림차순 정렬)
     * GET /api/works/genres-with-count?domain=GAME
     */
    @GetMapping("/genres-with-count")
    public ResponseEntity<java.util.Map<String, Long>> getGenresWithCount(
            @RequestParam(required = false) String domain
    ) {
        Domain domainEnum = null;
        if (domain != null && !domain.isBlank()) {
            try {
                domainEnum = Domain.valueOf(domain.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid domain parameter: {}", domain);
                return ResponseEntity.badRequest().build();
            }
        }

        java.util.Map<String, Long> genresWithCount = workApiService.getGenresWithCount(domainEnum);
        return ResponseEntity.ok(genresWithCount);
    }

    /**
     * 도메인별 사용 가능한 플랫폼 목록 조회
     * GET /api/works/platforms?domain=GAME
     */
    @GetMapping("/platforms")
    public ResponseEntity<java.util.List<String>> getPlatforms(
            @RequestParam(required = false) String domain
    ) {
        Domain domainEnum = null;
        if (domain != null && !domain.isBlank()) {
            try {
                domainEnum = Domain.valueOf(domain.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid domain parameter: {}", domain);
                return ResponseEntity.badRequest().build();
            }
        }

        java.util.List<String> platforms = workApiService.getAvailablePlatforms(domainEnum);
        return ResponseEntity.ok(platforms);
    }
}


