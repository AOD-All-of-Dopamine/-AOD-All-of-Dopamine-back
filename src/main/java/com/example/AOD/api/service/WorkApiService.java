package com.example.AOD.api.service;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.WorkResponseDTO;
import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.domain.Content;
import com.example.AOD.domain.entity.*;
import com.example.AOD.recommendation.repository.ContentRatingRepository;
import com.example.AOD.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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
    private final ContentRatingRepository contentRatingRepository;

    /**
     * 작품 목록 조회 (필터링, 페이징)
     */
    public PageResponse<WorkSummaryDTO> getWorks(Domain domain, String keyword, List<String> platforms, List<String> genres, Pageable pageable) {
        // 필터링이 필요한 경우 더 많은 데이터를 가져와서 필터링 후 페이징
        boolean needsFiltering = (platforms != null && !platforms.isEmpty()) || (genres != null && !genres.isEmpty());
        
        List<Content> allFilteredContent;
        
        if (needsFiltering) {
            // 필터링이 필요한 경우: 모든 데이터를 가져와서 필터링
            List<Content> allContent;
            if (keyword != null && !keyword.isBlank()) {
                if (domain != null) {
                    allContent = contentRepository.searchByDomainAndKeyword(domain, keyword, Pageable.unpaged()).getContent();
                } else {
                    allContent = contentRepository.searchByKeyword(keyword, Pageable.unpaged()).getContent();
                }
            } else if (domain != null) {
                allContent = contentRepository.findByDomain(domain, Pageable.unpaged()).getContent();
            } else {
                allContent = contentRepository.findAll(Pageable.unpaged()).getContent();
            }
            
            // 플랫폼 및 장르 필터링
            allFilteredContent = allContent.stream()
                    .filter(c -> filterByPlatforms(c, platforms))
                    .filter(c -> filterByGenres(c, genres))
                    .collect(Collectors.toList());
            
        } else {
            // 필터링이 없는 경우: 기존 방식대로 페이징된 데이터 가져오기
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
            allFilteredContent = contentPage.getContent();
        }
        
        // 정렬 적용
        if (pageable.getSort().isSorted()) {
            allFilteredContent = applySorting(allFilteredContent, pageable.getSort());
        }
        
        // 수동 페이징
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), allFilteredContent.size());
        
        List<WorkSummaryDTO> pagedContent = allFilteredContent.subList(
                Math.min(start, allFilteredContent.size()),
                end
        ).stream()
                .map(this::toWorkSummary)
                .collect(Collectors.toList());
        
        int totalElements = allFilteredContent.size();
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
        Double avg = contentRatingRepository.getAverageRatingByContentTypeAndId("GAME", contentId);
        if (avg == null) avg = contentRatingRepository.getAverageRatingByContentTypeAndId("AV", contentId);
        if (avg == null) avg = contentRatingRepository.getAverageRatingByContentTypeAndId("WEBTOON", contentId);
        if (avg == null) avg = contentRatingRepository.getAverageRatingByContentTypeAndId("WEBNOVEL", contentId);
        return avg != null ? avg : 0.0;
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
                    if (movie.getReleaseDate() != null) {
                        info.put("releaseDate", movie.getReleaseDate().toString());
                    }
                });
                break;
            case TV:
                tvContentRepository.findById(content.getContentId()).ifPresent(tv -> {
                    if (tv.getGenres() != null) info.put("genres", tv.getGenres());
                    info.put("seasonCount", tv.getSeasonCount());
                    info.put("episodeRuntime", tv.getEpisodeRuntime());
                    if (tv.getCast() != null) info.put("cast", tv.getCast());
                    if (tv.getFirstAirDate() != null) {
                        info.put("firstAirDate", tv.getFirstAirDate().toString());
                    }
                });
                break;
            case GAME:
                gameContentRepository.findById(content.getContentId()).ifPresent(game -> {
                    info.put("developer", game.getDeveloper());
                    info.put("publisher", game.getPublisher());
                    if (game.getGenres() != null) info.put("genres", game.getGenres());
                    if (game.getPlatforms() != null) info.putAll(game.getPlatforms());
                    if (game.getReleaseDate() != null) {
                        info.put("releaseDate", game.getReleaseDate().toString());
                    }
                });
                break;
            case WEBTOON:
                webtoonContentRepository.findById(content.getContentId()).ifPresent(webtoon -> {
                    info.put("author", webtoon.getAuthor());
                    info.put("illustrator", webtoon.getIllustrator());
                    info.put("status", webtoon.getStatus());
                    if (webtoon.getGenres() != null) {
                        info.put("genres", webtoon.getGenres());
                    }
                    if (webtoon.getStartedAt() != null) {
                        info.put("startedAt", webtoon.getStartedAt().toString());
                    }
                });
                break;
            case WEBNOVEL:
                webnovelContentRepository.findById(content.getContentId()).ifPresent(novel -> {
                    info.put("author", novel.getAuthor());
                    info.put("publisher", novel.getPublisher());
                    info.put("ageRating", novel.getAgeRating());
                    if (novel.getGenres() != null) {
                        info.put("genres", novel.getGenres());
                    }
                    if (novel.getStartedAt() != null) {
                        info.put("startedAt", novel.getStartedAt().toString());
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

        // 플랫폼 필터링
        List<Content> filteredContent = allContent.stream()
                .filter(c -> filterByPlatforms(c, platforms))
                .collect(Collectors.toList());

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

        // 플랫폼 필터링
        List<Content> filteredContent = allContent.stream()
                .filter(c -> filterByPlatforms(c, platforms))
                .collect(Collectors.toList());

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
     */
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
     * 장르 필터링 헬퍼 메서드 (복수 장르 지원)
     */
    private boolean filterByGenres(Content content, List<String> genres) {
        if (genres == null || genres.isEmpty()) {
            return true; // 필터링 없음
        }

        Domain domain = content.getDomain();
        List<String> contentGenres = getContentGenres(content, domain);
        
        // 선택된 장르 중 하나라도 포함되면 true
        return genres.stream()
                .anyMatch(genre -> contentGenres.stream()
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
     * 플랫폼 필터링 헬퍼 메서드 (단일 - 하위 호환성)
     * @deprecated Use filterByPlatforms instead
     */
    @Deprecated
    private boolean filterByPlatform(Content content, String platform) {
        if (platform == null || platform.isBlank()) {
            return true;
        }
        return filterByPlatforms(content, Collections.singletonList(platform));
    }

    /**
     * 장르 필터링 헬퍼 메서드 (단일 - 하위 호환성)
     * @deprecated Use filterByGenres instead
     */
    @Deprecated
    private boolean filterByGenre(Content content, String genre) {
        if (genre == null || genre.isBlank()) {
            return true;
        }
        return filterByGenres(content, Collections.singletonList(genre));
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
     */
    public List<String> getAvailablePlatforms(Domain domain) {
        Set<String> platformsSet = new HashSet<>();
        
        if (domain == null) {
            // 전체 플랫폼 조회
            platformDataRepository.findAll().forEach(pd -> {
                if (pd.getPlatformName() != null && !pd.getPlatformName().isBlank()) {
                    platformsSet.add(pd.getPlatformName());
                }
            });
        } else {
            // 특정 도메인의 플랫폼만 조회
            List<Content> contents = contentRepository.findByDomain(domain, Pageable.unpaged()).getContent();
            contents.forEach(content -> {
                List<PlatformData> platformDataList = platformDataRepository.findByContent(content);
                platformDataList.forEach(pd -> {
                    if (pd.getPlatformName() != null && !pd.getPlatformName().isBlank()) {
                        platformsSet.add(pd.getPlatformName());
                    }
                });
            });
        }
        
        return platformsSet.stream()
                .sorted()
                .collect(Collectors.toList());
    }
}
