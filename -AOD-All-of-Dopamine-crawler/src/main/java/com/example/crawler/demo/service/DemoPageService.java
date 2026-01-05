package com.example.crawler.demo.service;

import com.example.crawler.demo.dto.ContentDTO;
import com.example.crawler.demo.dto.ContentDetailDTO;
import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.entity.PlatformData;
import com.example.shared.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 데모 페이지에 필요한 비즈니스 로직을 처리하는 서비스
 */
@Service
@RequiredArgsConstructor
public class DemoPageService {

    private final ContentRepository contentRepository;
    private final PlatformDataRepository platformDataRepository;
    private final MovieContentRepository movieContentRepository;
    private final TvContentRepository tvContentRepository;
    private final GameContentRepository gameContentRepository;
    private final WebnovelContentRepository webnovelContentRepository;
    private final WebtoonContentRepository webtoonContentRepository;

    /**
     * 신작 콘텐츠 목록을 조회합니다. (최신순)
     * @param limit 조회할 개수
     * @return 최신 콘텐츠 DTO 리스트
     */
    public List<ContentDTO> getNewContents(int limit) {
        // Content 테이블에서 createdAt 필드를 기준으로 내림차순 정렬하여 상위 limit개의 데이터를 조회합니다.
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Content> contentPage = contentRepository.findAll(pageable);

        return contentPage.getContent().stream()
                .map(ContentDTO::new) // Content 엔티티를 ContentDTO로 변환
                .collect(Collectors.toList());
    }

    /**
     * 랭킹 콘텐츠 목록을 조회합니다.
     * 데모 버전에서는 최근에 확인된 데이터를 인기 있는 콘텐츠로 간주합니다.
     * @param limit 조회할 개수
     * @return 랭킹 콘텐츠 DTO 리스트
     */
    public List<ContentDTO> getRankingContents(int limit) {
        // PlatformData 테이블에서 lastSeenAt 필드를 기준으로 내림차순 정렬하여 상위 limit개의 데이터를 조회합니다.
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "lastSeenAt"));

        // PlatformData에서 Content를 lazy loading 할 수 있으므로, Content 정보가 필요한 DTO로 변환합니다.
        return platformDataRepository.findAll(pageable).getContent().stream()
                .map(platformData -> new ContentDTO(platformData.getContent()))
                .collect(Collectors.toList());
    }

    /**
     * 탐색 페이지를 위한 콘텐츠 목록을 도메인별로 조회합니다. (페이징)
     * @param domain 조회할 콘텐츠 도메인 (AV, GAME, WEBTOON, WEBNOVEL)
     * @param pageable 페이징 정보
     * @return 페이징 처리된 콘텐츠 DTO 페이지
     */
    public Page<ContentDTO> getExploreContents(Domain domain, Pageable pageable) {
        // [✏️ MODIFIED] Specification을 사용하는 대신, 새로 추가한 findByDomain 메서드를 호출합니다.
        Page<Content> contentPage = contentRepository.findByDomain(domain, pageable);

        // Page<Content>를 Page<ContentDTO>로 변환하여 반환합니다.
        return contentPage.map(ContentDTO::new);
    }

    /**
     * 콘텐츠 상세 정보를 조회합니다.
     * @param id 콘텐츠 ID
     * @return 콘텐츠 상세 정보 DTO
     */
    public ContentDetailDTO getContentDetails(Long id) {
        Content content = contentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid content Id:" + id));

        // 도메인별 추가 정보 조회
        Map<String, Object> domainAttributes = getDomainAttributes(content);

        // 플랫폼별 데이터 조회
        List<PlatformData> platformData = platformDataRepository.findByContent(content);

        return new ContentDetailDTO(content, domainAttributes, platformData);
    }

    private Map<String, Object> getDomainAttributes(Content content) {
        switch (content.getDomain()) {
            case MOVIE:
                return movieContentRepository.findById(content.getContentId())
                        .map(c -> {
                            Map<String, Object> attrs = new HashMap<>();
                            if (c.getContent().getReleaseDate() != null) attrs.put("releaseDate", c.getContent().getReleaseDate());
                            if (c.getGenres() != null) attrs.put("genres", c.getGenres());
                            if (c.getRuntime() != null) attrs.put("runtime", c.getRuntime());
                            if (c.getDirectors() != null) attrs.put("directors", c.getDirectors());
                            if (c.getCast() != null) attrs.put("cast", c.getCast());
                            return attrs;
                        })
                        .orElse(Collections.emptyMap());
            case TV:
                return tvContentRepository.findById(content.getContentId())
                        .map(c -> {
                            Map<String, Object> attrs = new HashMap<>();
                            if (c.getContent().getReleaseDate() != null) attrs.put("firstAirDate", c.getContent().getReleaseDate());
                            if (c.getGenres() != null) attrs.put("genres", c.getGenres());
                            if (c.getSeasonCount() != null) attrs.put("seasonCount", c.getSeasonCount());
                            if (c.getEpisodeRuntime() != null) attrs.put("episodeRuntime", c.getEpisodeRuntime());
                            if (c.getCast() != null) attrs.put("cast", c.getCast());
                            return attrs;
                        })
                        .orElse(Collections.emptyMap());
            case GAME:
                return gameContentRepository.findById(content.getContentId())
                        .map(c -> {
                            Map<String, Object> attrs = new HashMap<>();
                            if (c.getContent().getReleaseDate() != null) attrs.put("releaseDate", c.getContent().getReleaseDate());
                            if (c.getDeveloper() != null) attrs.put("developer", c.getDeveloper());
                            if (c.getPublisher() != null) attrs.put("publisher", c.getPublisher());
                            if (c.getGenres() != null) attrs.put("genres", c.getGenres());
                            return attrs;
                        })
                        .orElse(Collections.emptyMap());
            case WEBTOON:
                return webtoonContentRepository.findById(content.getContentId())
                        .map(c -> {
                            Map<String, Object> attrs = new HashMap<>();
                            if (c.getAuthor() != null) attrs.put("author", c.getAuthor());
                            if (c.getStatus() != null) attrs.put("status", c.getStatus());
                            if (c.getGenres() != null) attrs.put("genres", c.getGenres());
                            return attrs;
                        })
                        .orElse(Collections.emptyMap());
            case WEBNOVEL:
                return webnovelContentRepository.findById(content.getContentId())
                        .map(c -> {
                            Map<String, Object> attrs = new HashMap<>();
                            if (c.getAuthor() != null) attrs.put("author", c.getAuthor());
                            if (c.getContent().getReleaseDate() != null) attrs.put("startedAt", c.getContent().getReleaseDate());
                            if (c.getGenres() != null) attrs.put("genres", c.getGenres());
                            return attrs;
                        })
                        .orElse(Collections.emptyMap());
            default:
                return Collections.emptyMap();
        }
    }

}

