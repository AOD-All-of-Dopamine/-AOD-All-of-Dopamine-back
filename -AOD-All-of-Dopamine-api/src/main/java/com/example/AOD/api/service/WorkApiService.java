package com.example.AOD.api.service;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.WorkResponseDTO;
import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.shared.entity.Content;
import com.example.shared.entity.*;
// import com.example.AOD.recommendation.repository.ContentRatingRepository;
import com.example.shared.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    // private final ContentRatingRepository contentRatingRepository;
    
    // OTT Watch Providers (영화/시리즈에서 watchProviders 필터링에 사용)
    private static final Set<String> OTT_WATCH_PROVIDERS = Set.of(
            "netflix", "watcha", "disney plus", "tving", "wavve", "coupang play", "apple tv"
    );

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
        
        // 장르 필터링이 없고 플랫폼 필터링만 있는 경우 - 메모리 필터링
        if (platforms != null && !platforms.isEmpty()) {
            return getWorksByPlatforms(domain, keyword, platforms, pageable);
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
            log.warn("Genre filtering requires domain to be specified");
            return getWorksWithoutFiltering(null, keyword, pageable);
        }
        
        String[] genreArray = genres.toArray(new String[0]);
        List<Long> filteredContentIds = new ArrayList<>();
        
        // 각 도메인별로 DB에서 장르 필터링
        switch (domain) {
            case MOVIE:
                Page<MovieContent> moviePage = movieContentRepository.findByGenresContainingAll(genreArray, Pageable.unpaged());
                filteredContentIds = moviePage.getContent().stream()
                        .map(MovieContent::getContentId)
                        .collect(Collectors.toList());
                break;
            case TV:
                Page<TvContent> tvPage = tvContentRepository.findByGenresContainingAll(genreArray, Pageable.unpaged());
                filteredContentIds = tvPage.getContent().stream()
                        .map(TvContent::getContentId)
                        .collect(Collectors.toList());
                break;
            case GAME:
                Page<GameContent> gamePage = gameContentRepository.findByGenresContainingAll(genreArray, Pageable.unpaged());
                filteredContentIds = gamePage.getContent().stream()
                        .map(GameContent::getContentId)
                        .collect(Collectors.toList());
                break;
            case WEBTOON:
                Page<WebtoonContent> webtoonPage = webtoonContentRepository.findByGenresContainingAll(genreArray, Pageable.unpaged());
                filteredContentIds = webtoonPage.getContent().stream()
                        .map(WebtoonContent::getContentId)
                        .collect(Collectors.toList());
                break;
            case WEBNOVEL:
                Page<WebnovelContent> webnovelPage = webnovelContentRepository.findByGenresContainingAll(genreArray, Pageable.unpaged());
                filteredContentIds = webnovelPage.getContent().stream()
                        .map(WebnovelContent::getContentId)
                        .collect(Collectors.toList());
                break;
            default:
                log.warn("Unsupported domain for genre filtering: {}", domain);
                return PageResponse.<WorkSummaryDTO>builder()
                        .content(Collections.emptyList())
                        .page(0).size(0).totalElements(0L).totalPages(0)
                        .first(true).last(true).build();
        }
        
        if (filteredContentIds.isEmpty()) {
            return PageResponse.<WorkSummaryDTO>builder()
                    .content(Collections.emptyList())
                    .page(0).size(0).totalElements(0L).totalPages(0)
                    .first(true).last(true).build();
        }
        
        // Content ID로 Content 조회
        List<Content> filteredContents = contentRepository.findByContentIdIn(filteredContentIds);
        
        // 키워드 필터링 (있는 경우)
        if (keyword != null && !keyword.isBlank()) {
            String lowerKeyword = keyword.toLowerCase();
            filteredContents = filteredContents.stream()
                    .filter(c -> c.getMasterTitle().toLowerCase().contains(lowerKeyword) ||
                               (c.getOriginalTitle() != null && c.getOriginalTitle().toLowerCase().contains(lowerKeyword)))
                    .collect(Collectors.toList());
        }
        
        // 플랫폼 필터링 (있는 경우) - MOVIE/TV는 watchProviders, 나머지는 PlatformData
        if (platforms != null && !platforms.isEmpty()) {
            if (domain == Domain.MOVIE || domain == Domain.TV) {
                // OTT 플랫폼과 데이터 소스 플랫폼 분리
                List<String> lowerPlatforms = platforms.stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.toList());
                List<String> ottPlatforms = lowerPlatforms.stream()
                        .filter(OTT_WATCH_PROVIDERS::contains)
                        .collect(Collectors.toList());
                
                if (!ottPlatforms.isEmpty()) {
                    // watchProviders로 필터링
                    filteredContents = filteredContents.stream()
                            .filter(c -> filterByWatchProviders(c, ottPlatforms))
                            .collect(Collectors.toList());
                } else {
                    // 데이터 소스 플랫폼으로 필터링
                    filteredContents = filteredContents.stream()
                            .filter(c -> filterByPlatforms(c, platforms))
                            .collect(Collectors.toList());
                }
            } else {
                // GAME, WEBTOON, WEBNOVEL: 기존 방식
                filteredContents = filteredContents.stream()
                        .filter(c -> filterByPlatforms(c, platforms))
                        .collect(Collectors.toList());
            }
        }
        
        // 정렬 및 페이징 적용
        return applyPaginationAndMapping(filteredContents, pageable);
    }
    
    /**
     * 플랫폼 필터링만 있는 경우
     * ✨ 개선: 도메인 테이블의 platforms 컬럼을 이용한 직접 쿼리 (genres와 동일한 패턴)
     */
    private PageResponse<WorkSummaryDTO> getWorksByPlatforms(Domain domain, String keyword, List<String> platforms, Pageable pageable) {
        log.debug("getWorksByPlatforms - domain: {}, platforms: {}", domain, platforms);
        
        if (domain == null) {
            log.warn("Platform filtering requires domain to be specified");
            return getWorksWithoutFiltering(null, keyword, pageable);
        }
        
        // Sort 제거한 Pageable 생성 (Repository 쿼리에 ORDER BY가 고정되어 있음)
        Pageable pageableWithoutSort = PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize()
        );
        
        // 플랫폼 배열로 변환 (대소문자 그대로 유지)
        String[] platformArray = platforms.toArray(String[]::new);
        
        // 도메인별로 직접 쿼리 (genres와 동일한 패턴)
        Page<?> domainPage;
        switch (domain) {
            case MOVIE:
                domainPage = movieContentRepository.findByPlatformsContainingAll(platformArray, pageableWithoutSort);
                break;
            case TV:
                domainPage = tvContentRepository.findByPlatformsContainingAll(platformArray, pageableWithoutSort);
                break;
            case GAME:
                domainPage = gameContentRepository.findByPlatformsContainingAll(platformArray, pageableWithoutSort);
                break;
            case WEBTOON:
                domainPage = webtoonContentRepository.findByPlatformsContainingAll(platformArray, pageableWithoutSort);
                break;
            case WEBNOVEL:
                domainPage = webnovelContentRepository.findByPlatformsContainingAll(platformArray, pageableWithoutSort);
                break;
            default:
                return emptyResponse();
        }
        
        // Domain Content를 Content로 변환
        List<Content> contents = domainPage.getContent().stream()
            .map(d -> {
                switch (domain) {
                    case MOVIE: return ((MovieContent) d).getContent();
                    case TV: return ((TvContent) d).getContent();
                    case GAME: return ((GameContent) d).getContent();
                    case WEBTOON: return ((WebtoonContent) d).getContent();
                    case WEBNOVEL: return ((WebnovelContent) d).getContent();
                    default: return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        // 키워드 필터링 (있는 경우)
        if (keyword != null && !keyword.isBlank()) {
            String lowerKeyword = keyword.toLowerCase();
            contents = contents.stream()
                    .filter(c -> c.getMasterTitle().toLowerCase().contains(lowerKeyword) ||
                               (c.getOriginalTitle() != null && c.getOriginalTitle().toLowerCase().contains(lowerKeyword)))
                    .collect(Collectors.toList());
        }
        
        return PageResponse.<WorkSummaryDTO>builder()
                .content(contents.stream()
                        .map(this::toWorkSummary)
                        .collect(Collectors.toList()))
                .page(domainPage.getNumber())
                .size(domainPage.getSize())
                .totalElements(domainPage.getTotalElements())
                .totalPages(domainPage.getTotalPages())
                .first(domainPage.isFirst())
                .last(domainPage.isLast())
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
     * ⚠️ Deprecated: 기존 Watch Providers 기반 필터링 (복잡한 로직)
     * 새로운 platforms 컬럼 방식으로 대체됨
     */
    @Deprecated
    private PageResponse<WorkSummaryDTO> getWorksByWatchProviders(
            Domain domain, String keyword, List<String> ottPlatforms, List<String> sourcePlatforms, Pageable pageable) {
        
        log.debug("getWorksByWatchProviders - domain: {}, keyword: {}, ottPlatforms: {}, sourcePlatforms: {}", 
                  domain, keyword, ottPlatforms, sourcePlatforms);
        
        // 각 OTT별로 쿼리 후 교집합 구하기 (AND 방식)
        Set<Long> contentIdSet = null;
        for (String ottPlatform : ottPlatforms) {
            List<Long> ids = platformDataRepository.findContentIdsByDomainAndWatchProvider(
                    domain.name(), ottPlatform);
            if (contentIdSet == null) {
                contentIdSet = new HashSet<>(ids);
            } else {
                contentIdSet.retainAll(ids);  // 교집합 (AND)
            }
            
            // 교집합이 비어지면 더 이상 검색할 필요 없음
            if (contentIdSet.isEmpty()) {
                break;
            }
        }
        
        if (contentIdSet == null || contentIdSet.isEmpty()) {
            log.debug("No content found for watch providers: {}", ottPlatforms);
            return PageResponse.<WorkSummaryDTO>builder()
                    .content(Collections.emptyList())
                    .page(0).size(0).totalElements(0L).totalPages(0)
                    .first(true).last(true).build();
        }
        
        List<Long> contentIds = new ArrayList<>(contentIdSet);
        
        // Content ID로 Content 조회
        List<Content> filteredContents = contentRepository.findByContentIdIn(contentIds);
        
        // 도메인 필터링 (안전 장치)
        filteredContents = filteredContents.stream()
                .filter(c -> c.getDomain() == domain)
                .collect(Collectors.toList());
        
        // 키워드 필터링 (있는 경우)
        if (keyword != null && !keyword.isBlank()) {
            String lowerKeyword = keyword.toLowerCase();
            filteredContents = filteredContents.stream()
                    .filter(c -> c.getMasterTitle().toLowerCase().contains(lowerKeyword) ||
                               (c.getOriginalTitle() != null && c.getOriginalTitle().toLowerCase().contains(lowerKeyword)))
                    .collect(Collectors.toList());
        }
        
        // 데이터 소스 플랫폼 필터링 (TMDB_MOVIE, TMDB_TV 등이 추가로 선택된 경우)
        if (!sourcePlatforms.isEmpty()) {
            filteredContents = filteredContents.stream()
                    .filter(c -> filterByPlatforms(c, sourcePlatforms))
                    .collect(Collectors.toList());
        }
        
        // 정렬 및 페이징 적용
        return applyPaginationAndMapping(filteredContents, pageable);
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
            contentPage = contentRepository.findByDomain(domain, pageable);
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
     * 정렬 및 페이징 적용 후 DTO 매핑
     */
    private PageResponse<WorkSummaryDTO> applyPaginationAndMapping(List<Content> contents, Pageable pageable) {
        // 정렬 적용
        if (pageable.getSort().isSorted()) {
            contents = applySorting(contents, pageable.getSort());
        }
        
        // 수동 페이징
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), contents.size());
        
        List<WorkSummaryDTO> pagedContent = contents.subList(
                Math.min(start, contents.size()),
                end
        ).stream()
                .map(this::toWorkSummary)
                .collect(Collectors.toList());
        
        int totalElements = contents.size();
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
     * 정렬 적용 헬퍼 메서드
     */
    private List<Content> applySorting(List<Content> contents, Sort sort) {
        Comparator<Content> comparator = null;
        
        for (Sort.Order order : sort) {
            Comparator<Content> orderComparator = null;
            
            switch (order.getProperty()) {
                case "masterTitle":
                    orderComparator = Comparator.comparing(Content::getMasterTitle, 
                            Comparator.nullsLast(String::compareTo));
                    break;
                case "releaseDate":
                    orderComparator = Comparator.comparing(Content::getReleaseDate,
                            Comparator.nullsLast(LocalDate::compareTo));
                    break;
                default:
                    orderComparator = Comparator.comparing(Content::getContentId);
            }
            
            if (order.getDirection() == Sort.Direction.DESC) {
                orderComparator = orderComparator.reversed();
            }
            
            comparator = (comparator == null) ? orderComparator : comparator.thenComparing(orderComparator);
        }
        
        if (comparator != null) {
            return contents.stream().sorted(comparator).collect(Collectors.toList());
        }
        
        return contents;
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
                .score(calculateAverageScore(contentId))
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
                .score(calculateAverageScore(content.getContentId()))
                .releaseDate(content.getReleaseDate() != null ? content.getReleaseDate().toString() : null)
                .build();
    }

    /**
     * 평균 평점 계산
     */
    private Double calculateAverageScore(Long contentId) {
        // ContentRating의 contentType은 domain을 의미, contentId로 평균 계산
        // TODO: 추천 기능 추가 후 활성화
        // Double avg = contentRatingRepository.getAverageRatingByContentTypeAndId("GAME", contentId);
        // if (avg == null) avg = contentRatingRepository.getAverageRatingByContentTypeAndId("AV", contentId);
        // if (avg == null) avg = contentRatingRepository.getAverageRatingByContentTypeAndId("WEBTOON", contentId);
        // if (avg == null) avg = contentRatingRepository.getAverageRatingByContentTypeAndId("WEBNOVEL", contentId);
        // return avg != null ? avg : 0.0;
        return 0.0;
    }

    /**
     * 도메인별 상세 정보 추출
     */
    private Map<String, Object> getDomainInfo(Content content) {
        Map<String, Object> info = new HashMap<>();
        Domain domain = content.getDomain();

        switch (domain) {
            case MOVIE:
                movieContentRepository.findById(content.getContentId()).ifPresent(movie -> {
                    if (movie.getGenres() != null) info.put("genres", movie.getGenres());
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
                    if (tv.getGenres() != null) info.put("genres", tv.getGenres());
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
                    if (game.getGenres() != null) info.put("genres", game.getGenres());
                    if (game.getPlatforms() != null) info.put("platforms", game.getPlatforms());
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
                    if (webtoon.getGenres() != null) {
                        info.put("genres", webtoon.getGenres());
                    }
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
                    if (novel.getGenres() != null) {
                        info.put("genres", novel.getGenres());
                    }
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

        // 플랫폼 필터링 - MOVIE/TV는 watchProviders, 나머지는 PlatformData
        List<Content> filteredContent = filterContentByPlatforms(allContent, domain, platforms);

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
     * 출시 예정작 조회 (아직 출시되지 않은 작품들)
     */
    public PageResponse<WorkSummaryDTO> getUpcomingReleases(Domain domain, List<String> platforms, Pageable pageable) {
        LocalDate now = LocalDate.now();
        
        List<Content> allContent;
        if (domain != null) {
            allContent = contentRepository.findUpcomingReleases(domain, now, Pageable.unpaged()).getContent();
        } else {
            allContent = contentRepository.findUpcomingReleases(now, Pageable.unpaged()).getContent();
        }

        // 플랫폼 필터링 - MOVIE/TV는 watchProviders, 나머지는 PlatformData
        List<Content> filteredContent = filterContentByPlatforms(allContent, domain, platforms);

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
     * 플랫폼 필터링 헬퍼 메서드 (복수 플랫폼 지원)
     * @deprecated DB 레벨 필터링 사용 - findByPlatforms in ContentRepository
     * 메모리 필터링이 필요한 경우에만 사용
     */
    @Deprecated
    private boolean filterByPlatforms(Content content, List<String> platforms) {
        if (platforms == null || platforms.isEmpty()) {
            return true; // 필터링 없음
        }

        // PlatformData에서 플랫폼 확인
        List<PlatformData> platformDataList = platformDataRepository.findByContent(content);
        return platformDataList.stream()
                .anyMatch(pd -> platforms.stream()
                        .anyMatch(platform -> pd.getPlatformName().equalsIgnoreCase(platform)));
    }
    
    /**
     * Watch Provider (OTT) 필터링 헬퍼 메서드 (복수 OTT 지원) - 영화/시리즈 전용
     * PlatformData.attributes.watch_providers 필드에서 Netflix, Watcha 등의 OTT 플랫폼 확인
     */
    @SuppressWarnings("unchecked")
    private boolean filterByWatchProviders(Content content, List<String> ottPlatforms) {
        if (ottPlatforms == null || ottPlatforms.isEmpty()) {
            return true; // 필터링 없음
        }

        // PlatformData에서 watch_providers 확인
        List<PlatformData> platformDataList = platformDataRepository.findByContent(content);
        if (platformDataList.isEmpty()) {
            return false;
        }
        
        // 해당 컨텐츠의 모든 PlatformData에서 watch_providers 수집
        Set<String> contentWatchProviders = new HashSet<>();
        for (PlatformData pd : platformDataList) {
            Map<String, Object> attributes = pd.getAttributes();
            if (attributes != null && attributes.containsKey("watch_providers")) {
                Object watchProvidersObj = attributes.get("watch_providers");
                if (watchProvidersObj instanceof List) {
                    List<String> watchProviders = (List<String>) watchProvidersObj;
                    watchProviders.stream()
                            .map(String::toLowerCase)
                            .forEach(contentWatchProviders::add);
                }
            }
        }
        
        if (contentWatchProviders.isEmpty()) {
            return false;
        }
        
        // 선택된 OTT 플랫폼 중 하나라도 포함되어 있으면 true (OR 조건)
        return ottPlatforms.stream()
                .anyMatch(contentWatchProviders::contains);
    }
    
    /**
     * 콘텐츠 리스트에 대한 플랫폼 필터링 (공통 로직)
     * - MOVIE/TV 도메인: OTT Watch Provider로 필터링 (Netflix, Watcha 등)
     * - GAME/WEBTOON/WEBNOVEL 도메인: PlatformData로 필터링 (기존 방식)
     */
    private List<Content> filterContentByPlatforms(List<Content> contents, Domain domain, List<String> platforms) {
        if (platforms == null || platforms.isEmpty()) {
            return contents; // 필터링 없음
        }
        
        List<String> lowerPlatforms = platforms.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        
        // MOVIE/TV 도메인: OTT 플랫폼 분리 후 필터링
        if (domain != null && (domain == Domain.MOVIE || domain == Domain.TV)) {
            List<String> ottPlatforms = lowerPlatforms.stream()
                    .filter(OTT_WATCH_PROVIDERS::contains)
                    .collect(Collectors.toList());
            
            if (!ottPlatforms.isEmpty()) {
                // watchProviders로 필터링
                return contents.stream()
                        .filter(c -> filterByWatchProviders(c, ottPlatforms))
                        .collect(Collectors.toList());
            }
        }
        
        // GAME, WEBTOON, WEBNOVEL 또는 OTT가 아닌 플랫폼: 기존 PlatformData 필터링
        return contents.stream()
                .filter(c -> filterByPlatforms(c, platforms))
                .collect(Collectors.toList());
    }

    /**
     * 장르 필터링 헬퍼 메서드 (복수 장르 지원)
     * @deprecated DB 레벨 필터링 사용 - filterByGenresContainingAll in repositories
     * 메모리 필터링이 필요한 경우에만 사용
     */
    @Deprecated
    private boolean filterByGenres(Content content, List<String> genres) {
        if (genres == null || genres.isEmpty()) {
            return true; // 필터링 없음
        }

        Domain domain = content.getDomain();
        List<String> contentGenres = getContentGenres(content, domain);
        
        // 선택된 모든 장르가 포함되어야 true (AND 조건)
        return genres.stream()
                .allMatch(genre -> contentGenres.stream()
                        .anyMatch(cg -> cg.equalsIgnoreCase(genre)));
    }
    
    /**
     * 컨텐츠의 장르 목록 가져오기
     */
    private List<String> getContentGenres(Content content, Domain domain) {
        switch (domain) {
            case MOVIE:
                return movieContentRepository.findById(content.getContentId())
                        .map(movie -> movie.getGenres() != null ? new ArrayList<>(movie.getGenres()) : new ArrayList<String>())
                        .orElse(new ArrayList<>());
            case TV:
                return tvContentRepository.findById(content.getContentId())
                        .map(tv -> tv.getGenres() != null ? new ArrayList<>(tv.getGenres()) : new ArrayList<String>())
                        .orElse(new ArrayList<>());
            case GAME:
                return gameContentRepository.findById(content.getContentId())
                        .map(game -> game.getGenres() != null ? new ArrayList<>(game.getGenres()) : new ArrayList<String>())
                        .orElse(new ArrayList<>());
            case WEBTOON:
                return webtoonContentRepository.findById(content.getContentId())
                        .map(webtoon -> webtoon.getGenres() != null ? new ArrayList<>(webtoon.getGenres()) : new ArrayList<String>())
                        .orElse(new ArrayList<>());
            case WEBNOVEL:
                return webnovelContentRepository.findById(content.getContentId())
                        .map(novel -> novel.getGenres() != null ? new ArrayList<>(novel.getGenres()) : new ArrayList<String>())
                        .orElse(new ArrayList<>());
            default:
                return new ArrayList<>();
        }
    }

    /**
     * 도메인별 사용 가능한 장르 목록 조회
     */
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
     * 특정 도메인의 장르별 작품 수 카운트
     */
    private void addGenreCountsForDomain(Map<String, Long> genreCounts, Domain domain) {
        Collection<?> contents;
        
        switch (domain) {
            case MOVIE:
                contents = movieContentRepository.findAll();
                break;
            case TV:
                contents = tvContentRepository.findAll();
                break;
            case GAME:
                contents = gameContentRepository.findAll();
                break;
            case WEBTOON:
                contents = webtoonContentRepository.findAll();
                break;
            case WEBNOVEL:
                contents = webnovelContentRepository.findAll();
                break;
            default:
                return;
        }
        
        for (Object obj : contents) {
            List<String> genres = null;
            if (obj instanceof MovieContent) genres = ((MovieContent) obj).getGenres();
            else if (obj instanceof TvContent) genres = ((TvContent) obj).getGenres();
            else if (obj instanceof GameContent) genres = ((GameContent) obj).getGenres();
            else if (obj instanceof WebtoonContent) genres = ((WebtoonContent) obj).getGenres();
            else if (obj instanceof WebnovelContent) genres = ((WebnovelContent) obj).getGenres();
            
            if (genres != null) {
                for (String genre : genres) {
                    if (genre != null && !genre.isBlank()) {
                        genreCounts.merge(genre, 1L, Long::sum);
                    }
                }
            }
        }
    }

    /**
     * 특정 도메인의 장르 목록 수집
     */
    private Set<String> getGenresForDomain(Domain domain) {
        Set<String> genres = new HashSet<>();
        
        switch (domain) {
            case MOVIE:
                movieContentRepository.findAll().forEach(movie -> {
                    if (movie.getGenres() != null) {
                        genres.addAll(movie.getGenres());
                    }
                });
                break;
            case TV:
                tvContentRepository.findAll().forEach(tv -> {
                    if (tv.getGenres() != null) {
                        genres.addAll(tv.getGenres());
                    }
                });
                break;
            case GAME:
                gameContentRepository.findAll().forEach(game -> {
                    if (game.getGenres() != null) {
                        genres.addAll(game.getGenres());
                    }
                });
                break;
            case WEBTOON:
                webtoonContentRepository.findAll().forEach(webtoon -> {
                    if (webtoon.getGenres() != null) {
                        genres.addAll(webtoon.getGenres());
                    }
                });
                break;
            case WEBNOVEL:
                webnovelContentRepository.findAll().forEach(novel -> {
                    if (novel.getGenres() != null) {
                        genres.addAll(novel.getGenres());
                    }
                });
                break;
        }
        
        return genres;
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


