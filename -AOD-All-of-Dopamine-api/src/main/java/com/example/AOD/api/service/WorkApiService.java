package com.example.AOD.api.service;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.WorkResponseDTO;
import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.shared.entity.Content;
import com.example.shared.entity.*;
import com.example.AOD.repo.ReviewRepository;
import com.example.shared.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkApiService {

    private final ContentRepository contentRepository;
    private final MovieContentRepository movieContentRepository;
    private final TvContentRepository tvContentRepository;
    private final GameContentRepository gameContentRepository;
    private final WebtoonContentRepository webtoonContentRepository;
    private final WebnovelContentRepository webnovelContentRepository;
    private final PlatformDataRepository platformDataRepository;
    private final ReviewRepository reviewRepository;
    
    /**
     * 작품 목록 조회 (필터링, 페이징)
     * - 장르 필터링은 DB 레벨에서 처리 (성능 최적화)
     * - 플랫폼 필터링은 메모리에서 처리 (platform_data 조인 필요)
     */
    public PageResponse<WorkSummaryDTO> getWorks(Domain domain, String keyword, List<String> platforms, List<String> genres, Pageable pageable) {
        log.debug("getWorks - domain: {}, keyword: {}, platforms: {}, genres: {}, page: {}", 
                  domain, keyword, platforms, genres, pageable.getPageNumber());
        
        // 장르 필터링이 있는 경우 - DB 레벨에서 처리
        if (genres != null && !genres.isEmpty()) {
            return getWorksByGenresWithDbFiltering(domain, keyword, platforms, genres, pageable);
        }
        
        // 플랫폼만 있는 경우도 동일 통합 경로(findWorks 단일 쿼리)로 처리
        if (platforms != null && !platforms.isEmpty()) {
            return getWorksByGenresWithDbFiltering(domain, keyword, platforms, genres, pageable);
        }
        
        // 필터링 없는 기본 조회
        return getWorksWithoutFiltering(domain, keyword, pageable);
    }
    
    /**
     * 장르 필터링 - DB 레벨에서 처리 (PostgreSQL JSONB 쿼리 사용)
     */
    private PageResponse<WorkSummaryDTO> getWorksByGenresWithDbFiltering(
            Domain domain, String keyword, List<String> platforms, List<String> genres, Pageable pageable) {
        if (domain == null) {
            log.warn("Genre/platform filtering requires domain to be specified");
            return getWorksWithoutFiltering(null, keyword, pageable);
        }

        String[] genreArr = (genres == null || genres.isEmpty()) ? null : genres.toArray(new String[0]);
        String[] platformArr = (platforms == null || platforms.isEmpty()) ? null : platforms.toArray(new String[0]);
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword;
        // ORDER BY가 쿼리에 고정되어 있으므로 Sort 제거
        Pageable pageReq = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        // genres/platforms가 contents로 승격(2026-07)되어 도메인별 분기 없이 단일 쿼리
        Page<Content> page = contentRepository.findWorks(domain.name(), genreArr, platformArr, kw, pageReq);

        List<WorkSummaryDTO> dtos = page.getContent().stream()
                .map(this::toWorkSummary)
                .collect(Collectors.toList());

        return PageResponse.<WorkSummaryDTO>builder()
                .content(dtos)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    
    /**
     * 빈 응답 반환
     */
    private PageResponse<WorkSummaryDTO> emptyResponse() {
        return PageResponse.<WorkSummaryDTO>builder()
                .content(Collections.emptyList())
                .page(0).size(0).totalElements(0L).totalPages(0)
                .first(true).last(true).build();
    }

    
    /**
     * 필터링 없는 기본 조회
     */
    private PageResponse<WorkSummaryDTO> getWorksWithoutFiltering(Domain domain, String keyword, Pageable pageable) {
        Page<Content> contentPage;
        if (keyword != null && !keyword.isBlank()) {
            if (domain != null) {
                contentPage = contentRepository.searchByDomainAndKeyword(domain, keyword, pageable);
            } else {
                contentPage = contentRepository.searchByKeyword(keyword, pageable);
            }
        } else if (domain != null) {
            Pageable pageableWithoutSort = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
            LocalDate maxDate = LocalDate.now().plusYears(1);
            contentPage = contentRepository.findByDomainOrderByReleaseDateDesc(domain.name(), maxDate, pageableWithoutSort);
        } else {
            contentPage = contentRepository.findAll(pageable);
        }
        
        return PageResponse.<WorkSummaryDTO>builder()
                .content(contentPage.getContent().stream()
                        .map(this::toWorkSummary)
                        .collect(Collectors.toList()))
                .page(contentPage.getNumber())
                .size(contentPage.getSize())
                .totalElements(contentPage.getTotalElements())
                .totalPages(contentPage.getTotalPages())
                .first(contentPage.isFirst())
                .last(contentPage.isLast())
                .build();
    }
    
    

    /**
     * 작품 상세 조회
     */
    public WorkResponseDTO getWorkDetail(Long contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new RuntimeException("Content not found: " + contentId));

        WorkResponseDTO dto = WorkResponseDTO.builder()
                .id(content.getContentId())
                .domain(content.getDomain().name())
                .title(content.getMasterTitle())
                .originalTitle(content.getOriginalTitle())
                .releaseDate(content.getReleaseDate() != null ? content.getReleaseDate().toString() : null)
                .thumbnail(content.getPosterImageUrl())
                .synopsis(content.getSynopsis())
                .score(content.getAverageScore())
                .build();

        // 도메인별 상세 정보 추가
        dto.setDomainInfo(getDomainInfo(content));

        // 플랫폼별 정보 추가
        dto.setPlatformInfo(getPlatformInfo(contentId));

        return dto;
    }

    /**
     * WorkSummaryDTO 변환
     */
    private WorkSummaryDTO toWorkSummary(Content content) {
        return WorkSummaryDTO.builder()
                .id(content.getContentId())
                .domain(content.getDomain().name())
                .title(content.getMasterTitle())
                .thumbnail(content.getPosterImageUrl())
                .score(content.getAverageScore())
                .releaseDate(content.getReleaseDate() != null ? content.getReleaseDate().toString() : null)
                .build();
    }

    /**
     * 도메인별 상세 정보 추출
     */
    private Map<String, Object> getDomainInfo(Content content) {
        Map<String, Object> info = new HashMap<>();
        Domain domain = content.getDomain();

        // genres/platforms는 마스터 공통 속성 (2026-07 도메인 테이블에서 승격)
        if (content.getGenres() != null && !content.getGenres().isEmpty()) {
            info.put("genres", content.getGenres());
        }
        if (content.getPlatforms() != null && !content.getPlatforms().isEmpty()) {
            info.put("platforms", content.getPlatforms());
        }

        switch (domain) {
            case MOVIE:
                movieContentRepository.findById(content.getContentId()).ifPresent(movie -> {
                    info.put("runtime", movie.getRuntime());
                    if (movie.getDirectors() != null) info.put("directors", movie.getDirectors());
                    if (movie.getCast() != null) info.put("cast", movie.getCast());
                    if (movie.getContent().getReleaseDate() != null) {
                        info.put("releaseDate", movie.getContent().getReleaseDate().toString());
                    }
                });
                break;
            case TV:
                tvContentRepository.findById(content.getContentId()).ifPresent(tv -> {
                    info.put("seasonCount", tv.getSeasonCount());
                    info.put("episodeRuntime", tv.getEpisodeRuntime());
                    if (tv.getCast() != null) info.put("cast", tv.getCast());
                    if (tv.getContent().getReleaseDate() != null) {
                        info.put("firstAirDate", tv.getContent().getReleaseDate().toString());
                    }
                });
                break;
            case GAME:
                gameContentRepository.findById(content.getContentId()).ifPresent(game -> {
                    info.put("developer", game.getDeveloper());
                    info.put("publisher", game.getPublisher());
                    if (game.getContent().getReleaseDate() != null) {
                        info.put("releaseDate", game.getContent().getReleaseDate().toString());
                    }
                });
                break;
            case WEBTOON:
                webtoonContentRepository.findById(content.getContentId()).ifPresent(webtoon -> {
                    info.put("author", webtoon.getAuthor());
                    info.put("status", webtoon.getStatus());
                    info.put("weekday", webtoon.getWeekday());
                });
                // releaseDate는 Content에서 가져옴
                if (content.getReleaseDate() != null) {
                    info.put("releaseDate", content.getReleaseDate().toString());
                }
                break;
            case WEBNOVEL:
                webnovelContentRepository.findById(content.getContentId()).ifPresent(novel -> {
                    info.put("author", novel.getAuthor());
                    info.put("publisher", novel.getPublisher());
                    info.put("ageRating", novel.getAgeRating());
                    if (novel.getContent().getReleaseDate() != null) {
                        info.put("startedAt", novel.getContent().getReleaseDate().toString());
                    }
                });
                break;
        }

        return info;
    }

    /**
     * 플랫폼별 정보 추출
     */
    private Map<String, Map<String, Object>> getPlatformInfo(Long contentId) {
        Map<String, Map<String, Object>> platformInfo = new HashMap<>();

        Content content = contentRepository.findById(contentId).orElse(null);
        if (content == null) return platformInfo;

        List<PlatformData> platformDataList = platformDataRepository.findByContent(content);

        for (PlatformData pd : platformDataList) {
            Map<String, Object> info = new HashMap<>();
            info.put("url", pd.getUrl());
            info.put("platformSpecificId", pd.getPlatformSpecificId());
            if (pd.getAttributes() != null) {
                info.putAll(pd.getAttributes());
            }
            platformInfo.put(pd.getPlatformName(), info);
        }

        return platformInfo;
    }

    /**
     * 최근 출시작 조회 (최근 3개월 이내 출시된 작품들)
     */
    public PageResponse<WorkSummaryDTO> getRecentReleases(Domain domain, List<String> platforms, Pageable pageable) {
        LocalDate now = LocalDate.now();
        LocalDate threeMonthsAgo = now.minusMonths(3);
        
        List<Content> allContent;
        if (domain != null) {
            allContent = contentRepository.findReleasesInDateRange(domain, threeMonthsAgo, now, Pageable.unpaged()).getContent();
        } else {
            allContent = contentRepository.findReleasesInDateRange(threeMonthsAgo, now, Pageable.unpaged()).getContent();
        }

        // 플랫폼 필터링 (contents.platforms 배열 — OTT 포함)
        List<Content> filteredContent = filterContentByPlatforms(allContent, platforms);

        // 수동 페이징
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filteredContent.size());
        
        List<WorkSummaryDTO> pagedContent = filteredContent.subList(
                Math.min(start, filteredContent.size()),
                end
        ).stream()
                .map(this::toWorkSummary)
                .collect(Collectors.toList());
        
        int totalElements = filteredContent.size();
        int totalPages = (int) Math.ceil((double) totalElements / pageable.getPageSize());

        return PageResponse.<WorkSummaryDTO>builder()
                .content(pagedContent)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .totalElements((long) totalElements)
                .totalPages(totalPages)
                .first(pageable.getPageNumber() == 0)
                .last(pageable.getPageNumber() >= totalPages - 1)
                .build();
    }

    /**
     * [✨ 신규 기능] 최근 리뷰가 달린 작품 조회
     */
    public PageResponse<WorkSummaryDTO> getRecentReviewedWorks(Domain domain, List<String> platforms, Pageable pageable) {
        Page<Content> contentPage;
        if (domain != null) {
            contentPage = contentRepository.findRecentlyReviewedContentsByDomainNative(domain.name(), pageable);
        } else {
            contentPage = contentRepository.findRecentlyReviewedContentsNative(pageable);
        }

        List<Content> allContent = contentPage.getContent();

        // 플랫폼 필터링 (contents.platforms 배열 — OTT 포함)
        List<Content> filteredContent = filterContentByPlatforms(allContent, platforms);

        List<WorkSummaryDTO> pagedContent = filteredContent.stream()
                .map(this::toWorkSummary)
                .collect(Collectors.toList());

        return PageResponse.<WorkSummaryDTO>builder()
                .content(pagedContent)
                .page(contentPage.getNumber())
                .size(contentPage.getSize())
                .totalElements(contentPage.getTotalElements())
                .totalPages(contentPage.getTotalPages())
                .first(contentPage.isFirst())
                .last(contentPage.isLast())
                .build();
    }

    /**
     * 출시 예정작 조회 (아직 출시되지 않은 작품들)
     */
    public PageResponse<WorkSummaryDTO> getUpcomingReleases(Domain domain, List<String> platforms, Pageable pageable) {
        LocalDate now = LocalDate.now();
        LocalDate oneYearLater = now.plusYears(1);
        
        List<Content> allContent;
        if (domain != null) {
            allContent = contentRepository.findUpcomingReleases(domain, now, oneYearLater, Pageable.unpaged()).getContent();
        } else {
            allContent = contentRepository.findUpcomingReleases(now, oneYearLater, Pageable.unpaged()).getContent();
        }

        // 플랫폼 필터링 (contents.platforms 배열 — OTT 포함)
        List<Content> filteredContent = filterContentByPlatforms(allContent, platforms);

        // 수동 페이징
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filteredContent.size());
        
        List<WorkSummaryDTO> pagedContent = filteredContent.subList(
                Math.min(start, filteredContent.size()),
                end
        ).stream()
                .map(this::toWorkSummary)
                .collect(Collectors.toList());
        
        int totalElements = filteredContent.size();
        int totalPages = (int) Math.ceil((double) totalElements / pageable.getPageSize());

        return PageResponse.<WorkSummaryDTO>builder()
                .content(pagedContent)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .totalElements((long) totalElements)
                .totalPages(totalPages)
                .first(pageable.getPageNumber() == 0)
                .last(pageable.getPageNumber() >= totalPages - 1)
                .build();
    }

    
    
    /**
     * 콘텐츠 리스트에 대한 플랫폼 필터링 (공통 로직)
     * contents.platforms 배열(수집 플랫폼 + OTT, 2026-07 승격)로 검사 — 대소문자 무시, OR 조건.
     * (과거 콘텐츠당 platform_data 재조회 N+1 제거)
     */
    private List<Content> filterContentByPlatforms(List<Content> contents, List<String> platforms) {
        if (platforms == null || platforms.isEmpty()) {
            return contents; // 필터링 없음
        }

        Set<String> wanted = platforms.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        return contents.stream()
                .filter(c -> c.getPlatforms() != null && c.getPlatforms().stream()
                        .map(String::toLowerCase)
                        .anyMatch(wanted::contains))
                .collect(Collectors.toList());
    }

    /**
     * 도메인별 사용 가능한 장르 목록 조회 (genres는 contents로 승격됨 — 2026-07)
     */
    @Cacheable(value = "availableGenres", key = "#domain != null ? #domain.name() : 'ALL'")
    public List<String> getAvailableGenres(Domain domain) {
        Set<String> genresSet = new HashSet<>();
        
        if (domain == null) {
            // 전체 도메인의 장르 수집
            genresSet.addAll(getGenresForDomain(Domain.MOVIE));
            genresSet.addAll(getGenresForDomain(Domain.TV));
            genresSet.addAll(getGenresForDomain(Domain.GAME));
            genresSet.addAll(getGenresForDomain(Domain.WEBTOON));
            genresSet.addAll(getGenresForDomain(Domain.WEBNOVEL));
        } else {
            genresSet.addAll(getGenresForDomain(domain));
        }
        
        return genresSet.stream()
                .filter(genre -> genre != null && !genre.isBlank())
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 도메인별 장르별 작품 수 조회 (작품 수 기준 내림차순 정렬)
     */
    @Cacheable(value = "genresWithCount", key = "#domain != null ? #domain.name() : 'ALL'")
    public Map<String, Long> getGenresWithCount(Domain domain) {
        Map<String, Long> genreCounts = new HashMap<>();
        
        if (domain == null) {
            // 전체 도메인의 장르별 카운트
            addGenreCountsForDomain(genreCounts, Domain.MOVIE);
            addGenreCountsForDomain(genreCounts, Domain.TV);
            addGenreCountsForDomain(genreCounts, Domain.GAME);
            addGenreCountsForDomain(genreCounts, Domain.WEBTOON);
            addGenreCountsForDomain(genreCounts, Domain.WEBNOVEL);
        } else {
            addGenreCountsForDomain(genreCounts, domain);
        }
        
        // 작품 수 기준 내림차순 정렬하여 LinkedHashMap으로 반환
        return genreCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    /**
     * 장르 캐시 주기적 무효화 — 크롤러가 새 콘텐츠를 추가하면 장르 분포가 바뀌므로 30분마다 갱신
     */
    @Scheduled(fixedRate = 1_800_000L)
    @CacheEvict(value = {"genresWithCount", "availableGenres"}, allEntries = true)
    public void evictGenreCaches() {
        log.debug("Evicted genre caches (scheduled refresh)");
    }

    /**
     * 특정 도메인의 장르별 작품 수 카운트
     */
    private void addGenreCountsForDomain(Map<String, Long> genreCounts, Domain domain) {
        for (Object[] row : contentRepository.countByGenre(domain.name())) {
            String genre = (String) row[0];
            if (genre == null || genre.isBlank()) continue;
            long count = ((Number) row[1]).longValue();
            genreCounts.merge(genre, count, Long::sum);
        }
    }

    /**
     * 특정 도메인의 장르 목록 수집
     */
    private Set<String> getGenresForDomain(Domain domain) {
        return new HashSet<>(contentRepository.findDistinctGenres(domain.name()));
    }

    /**
     * 도메인별 사용 가능한 플랫폼 목록 조회
     * - DB 조회 없이 설정 파일에서 바로 반환 (성능 최적화)
     * - 플랫폼은 고정값이므로 application.properties에 정의
     */
    public List<String> getAvailablePlatforms(Domain domain) {
        if (domain == null) {
            // 전체 플랫폼 반환
            return List.of("TMDB_MOVIE", "TMDB_TV", "Steam", "NaverWebtoon", "NaverSeries", "KakaoPage");
        }
        
        // 도메인별 플랫폼 반환
        return switch (domain) {
            case MOVIE -> List.of("TMDB_MOVIE");
            case TV -> List.of("TMDB_TV");
            case GAME -> List.of("Steam");
            case WEBTOON -> List.of("NaverWebtoon");
            case WEBNOVEL -> List.of("NaverSeries", "KakaoPage");
        };
    }
}

