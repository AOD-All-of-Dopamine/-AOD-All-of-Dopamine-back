package com.example.AOD.api.service;

import com.example.AOD.api.dto.WorkFilters;
import com.example.AOD.api.dto.WorkResponseDTO;
import com.example.AOD.repo.ReviewRepository;
import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.repository.ContentRepository;
import com.example.shared.repository.GameContentRepository;
import com.example.shared.repository.MovieContentRepository;
import com.example.shared.repository.PlatformDataRepository;
import com.example.shared.repository.TvContentRepository;
import com.example.shared.repository.WebnovelContentRepository;
import com.example.shared.repository.WebtoonContentRepository;
import com.example.shared.repository.WorksFilterCriteria;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * WorkApiService 단위 테스트 (2026-08 Steam 정제 · 2026-09 동적 조립 전환).
 * 성인 제외의 SQL 조건 자체는 ContentRepositoryAdultFilterTest/WorksQueryBuilderTest가 지키고,
 * 여기서는 서비스 계층의 축 전달·상세 접근 허용 계약을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class WorkApiServiceTest {

    @Mock private ContentRepository contentRepository;
    @Mock private MovieContentRepository movieContentRepository;
    @Mock private TvContentRepository tvContentRepository;
    @Mock private GameContentRepository gameContentRepository;
    @Mock private WebtoonContentRepository webtoonContentRepository;
    @Mock private WebnovelContentRepository webnovelContentRepository;
    @Mock private PlatformDataRepository platformDataRepository;
    @Mock private ReviewRepository reviewRepository;

    @InjectMocks private WorkApiService workApiService;

    private WorksFilterCriteria captureCriteria(Domain domain, String keyword, WorkFilters filters) {
        Page<Content> empty = new PageImpl<>(List.of());
        given(contentRepository.findWorks(any(WorksFilterCriteria.class), any(Pageable.class)))
                .willReturn(empty);

        workApiService.getWorks(domain, keyword, filters, PageRequest.of(0, 20));

        ArgumentCaptor<WorksFilterCriteria> captor = ArgumentCaptor.forClass(WorksFilterCriteria.class);
        verify(contentRepository).findWorks(captor.capture(), any(Pageable.class));
        return captor.getValue();
    }

    @Test
    void reviewCountMinAloneTriggersFindWorksAndIsPassedThrough() {
        // 게임 탭 리뷰 수 필터 단독 사용 — DB 필터 경로(findWorks)로 가고 축이 그대로 전달돼야 함
        WorkFilters filters = new WorkFilters(null, null, null, null, null, null, null, 100);
        assertTrue(filters.hasAny(), "reviewCountMin 단독으로도 필터 축으로 인정돼야 함");

        WorksFilterCriteria c = captureCriteria(Domain.GAME, null, filters);

        assertEquals("GAME", c.domain());
        assertEquals(100, c.reviewCountMin());
        assertNull(c.genres());
        assertNull(c.platforms());
        assertNull(c.keyword());
        assertNull(c.releaseFrom());
        assertNull(c.releaseTo());
        assertNull(c.status());
    }

    @Test
    void releaseDatesAreParsedAndBlankKeywordIsDropped() {
        // 컨트롤러가 yyyy-MM-dd 검증을 끝낸 문자열을 LocalDate로 변환해 넘긴다 (SQL CAST 불필요)
        WorkFilters filters = new WorkFilters(List.of("액션"), null, "2025-01-01", "2025-12-31",
                null, null, null, null);

        WorksFilterCriteria c = captureCriteria(Domain.MOVIE, "   ", filters);

        assertEquals("MOVIE", c.domain());
        assertEquals(List.of("액션"), c.genres());
        assertEquals(LocalDate.of(2025, 1, 1), c.releaseFrom());
        assertEquals(LocalDate.of(2025, 12, 31), c.releaseTo());
        assertNull(c.keyword(), "공백 키워드는 축 꺼짐(null)으로 정규화");
        assertNull(c.reviewCountMin());
    }

    @Test
    void legacyPathUsesLegacyQueryWithSameAxesAndSkipsDynamicPath() {
        // A/B 측정용 임시 경로 (troubleshooting/07 §8-1): 구 WORKS_FILTER 쿼리로 가고, 동적 경로는 호출하지 않는다
        WorkFilters filters = new WorkFilters(null, null, null, null, null, null, null, 100);
        Page<Content> empty = new PageImpl<>(List.of());
        given(contentRepository.findWorksLegacy(eq("GAME"), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(100), any(Pageable.class)))
                .willReturn(empty);

        workApiService.getWorksLegacy(Domain.GAME, null, filters, PageRequest.of(0, 20));

        verify(contentRepository).findWorksLegacy(eq("GAME"), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(100), any(Pageable.class));
        verify(contentRepository, never()).findWorks(any(WorksFilterCriteria.class), any(Pageable.class));
    }

    @Test
    void adultContentDetailIsStillAccessible() {
        // 성인 콘텐츠는 목록에서만 제외 — 상세 직접 접근(findById)은 허용이 요구 범위
        Content adult = new Content();
        adult.setContentId(7L);
        adult.setDomain(Domain.GAME);
        adult.setMasterTitle("Adult Game");
        adult.setIsAdult(true);

        given(contentRepository.findById(7L)).willReturn(Optional.of(adult));
        given(gameContentRepository.findById(7L)).willReturn(Optional.empty());
        given(platformDataRepository.findByContent(adult)).willReturn(List.of());

        WorkResponseDTO dto = workApiService.getWorkDetail(7L);

        assertEquals("Adult Game", dto.getTitle());
        assertEquals("GAME", dto.getDomain());
    }
}
