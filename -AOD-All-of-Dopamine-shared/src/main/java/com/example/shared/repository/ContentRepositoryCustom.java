package com.example.shared.repository;

import com.example.shared.entity.Content;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * ContentRepository의 동적 조립 경로 (구현: {@link ContentRepositoryImpl}).
 * Spring Data가 인터페이스명 + "Impl" 규약으로 자동 결합한다.
 */
public interface ContentRepositoryCustom {

    /**
     * 통합 필터 목록 조회 — 켜진 필터 축만 SQL에 포함한다 ({@link WorksQueryBuilder}).
     * 정렬은 release_date DESC NULLS LAST, content_id ASC 고정 (Pageable의 Sort는 무시).
     * 성인 제외(is_adult = false)는 항상 적용.
     */
    Page<Content> findWorks(WorksFilterCriteria criteria, Pageable pageable);
}
