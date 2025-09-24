package com.example.AOD.demo.service;

import com.example.AOD.demo.dto.ContentDTO;
import com.example.AOD.domain.Content;
import com.example.AOD.domain.entity.Domain;
import com.example.AOD.repo.ContentRepository;
import com.example.AOD.repo.PlatformDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 데모 페이지에 필요한 비즈니스 로직을 처리하는 서비스
 */
@Service
@RequiredArgsConstructor
public class DemoPageService {

    private final ContentRepository contentRepository;
    private final PlatformDataRepository platformDataRepository;

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

}