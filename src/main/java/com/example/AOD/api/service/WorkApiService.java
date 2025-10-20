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
    private final AvContentRepository avContentRepository;
    private final GameContentRepository gameContentRepository;
    private final WebtoonContentRepository webtoonContentRepository;
    private final WebnovelContentRepository webnovelContentRepository;
    private final PlatformDataRepository platformDataRepository;
    private final ContentRatingRepository contentRatingRepository;

    /**
     * 작품 목록 조회 (필터링, 페이징)
     */
    public PageResponse<WorkSummaryDTO> getWorks(Domain domain, String keyword, Pageable pageable) {
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

        List<WorkSummaryDTO> content = contentPage.getContent().stream()
                .map(this::toWorkSummary)
                .collect(Collectors.toList());

        return PageResponse.<WorkSummaryDTO>builder()
                .content(content)
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
            case AV:
                avContentRepository.findById(content.getContentId()).ifPresent(av -> {
                    if (av.getGenres() != null) info.putAll(av.getGenres());
                    info.put("tmdbId", av.getTmdbId());
                    info.put("avType", av.getAvType());
                    if (av.getReleaseDate() != null) {
                        info.put("releaseDate", av.getReleaseDate().toString());
                    }
                });
                break;
            case GAME:
                gameContentRepository.findById(content.getContentId()).ifPresent(game -> {
                    info.put("developer", game.getDeveloper());
                    info.put("publisher", game.getPublisher());
                    if (game.getGenres() != null) info.putAll(game.getGenres());
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
                    if (webtoon.getGenres() != null) info.putAll(webtoon.getGenres());
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
    public PageResponse<WorkSummaryDTO> getRecentReleases(Domain domain, Pageable pageable) {
        LocalDate now = LocalDate.now();
        LocalDate threeMonthsAgo = now.minusMonths(3);
        Page<Content> contentPage;

        if (domain != null) {
            contentPage = contentRepository.findReleasesInDateRange(domain, threeMonthsAgo, now, pageable);
        } else {
            contentPage = contentRepository.findReleasesInDateRange(threeMonthsAgo, now, pageable);
        }

        List<WorkSummaryDTO> content = contentPage.getContent().stream()
                .map(this::toWorkSummary)
                .collect(Collectors.toList());

        return PageResponse.<WorkSummaryDTO>builder()
                .content(content)
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
    public PageResponse<WorkSummaryDTO> getUpcomingReleases(Domain domain, Pageable pageable) {
        LocalDate now = LocalDate.now();
        Page<Content> contentPage;

        if (domain != null) {
            contentPage = contentRepository.findUpcomingReleases(domain, now, pageable);
        } else {
            contentPage = contentRepository.findUpcomingReleases(now, pageable);
        }

        List<WorkSummaryDTO> content = contentPage.getContent().stream()
                .map(this::toWorkSummary)
                .collect(Collectors.toList());

        return PageResponse.<WorkSummaryDTO>builder()
                .content(content)
                .page(contentPage.getNumber())
                .size(contentPage.getSize())
                .totalElements(contentPage.getTotalElements())
                .totalPages(contentPage.getTotalPages())
                .first(contentPage.isFirst())
                .last(contentPage.isLast())
                .build();
    }
}
