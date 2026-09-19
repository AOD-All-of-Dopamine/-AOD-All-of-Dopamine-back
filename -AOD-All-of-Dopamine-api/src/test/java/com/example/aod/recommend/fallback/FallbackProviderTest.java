package com.example.AOD.recommend.fallback;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.WorkFilters;
import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.api.service.WorkApiService;
import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.entity.ExternalRanking;
import com.example.shared.repository.ExternalRankingRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FallbackProviderTest {

    private final ExternalRankingRepository rankings = mock(ExternalRankingRepository.class);
    private final WorkApiService workApiService = mock(WorkApiService.class);
    private final FallbackProvider provider = new FallbackProvider(rankings, workApiService);

    private static Content content(long id, boolean adult) {
        Content c = new Content();
        c.setContentId(id);
        c.setDomain(Domain.GAME);
        c.setMasterTitle("작품 " + id);
        c.setIsAdult(adult);
        return c;
    }

    private static ExternalRanking row(String platform, int rank, Content content) {
        ExternalRanking er = new ExternalRanking();
        er.setPlatform(platform);
        er.setRanking(rank);
        er.setTitle("t" + rank);
        er.setPlatformSpecificId(String.valueOf(rank));
        er.setContent(content);
        return er;
    }

    private static WorkSummaryDTO dto(long id) {
        return WorkSummaryDTO.builder().id(id).domain("GAME").title("작품 " + id).build();
    }

    private void givenEnrichEchoesIds() {
        given(workApiService.toEnrichedSummaries(anyList())).willAnswer(invocation -> {
            List<Content> contents = invocation.getArgument(0);
            return contents.stream().map(c -> dto(c.getContentId())).toList();
        });
    }

    @Test
    void singleTabUsesItsPlatformRankingAndSkipsUnlinkedAndAdultRows() {
        givenEnrichEchoesIds();
        given(rankings.findByPlatformWithContent("Steam")).willReturn(List.of(
                row("Steam", 1, null),                 // 미매핑 — JPQL 이 남겨 두므로 여기서 거른다
                row("Steam", 2, content(20L, false)),
                row("Steam", 3, content(30L, true)),   // 성인
                row("Steam", 4, content(20L, false)),  // 같은 작품 두 번
                row("Steam", 5, content(50L, false))));

        List<WorkSummaryDTO> works = provider.byTab("game", 20);

        assertEquals(List.of(20L, 50L), works.stream().map(WorkSummaryDTO::getId).toList());
        verify(workApiService, never()).getWorks(any(), anyString(), any(), any());
    }

    @Test
    void allTabInterleavesSteamMovieTvWebnovel() {
        givenEnrichEchoesIds();
        given(rankings.findByPlatformWithContent("Steam"))
                .willReturn(List.of(row("Steam", 1, content(1L, false)), row("Steam", 2, content(2L, false))));
        given(rankings.findByPlatformWithContent("TMDB_MOVIE"))
                .willReturn(List.of(row("TMDB_MOVIE", 1, content(3L, false))));
        given(rankings.findByPlatformWithContent("TMDB_TV"))
                .willReturn(List.of(row("TMDB_TV", 1, content(4L, false))));
        given(rankings.findByPlatformWithContent("NaverSeries"))
                .willReturn(List.of(row("NaverSeries", 1, content(5L, false))));

        List<WorkSummaryDTO> works = provider.byTab("all", 20);

        assertEquals(List.of(1L, 3L, 4L, 5L, 2L), works.stream().map(WorkSummaryDTO::getId).toList());
        verify(rankings, never()).findByPlatformWithContent("NaverWebtoon");
    }

    @Test
    void sizeIsRespected() {
        givenEnrichEchoesIds();
        given(rankings.findByPlatformWithContent("NaverWebtoon")).willReturn(List.of(
                row("NaverWebtoon", 1, content(1L, false)),
                row("NaverWebtoon", 2, content(2L, false)),
                row("NaverWebtoon", 3, content(3L, false))));

        assertEquals(2, provider.byTab("webtoon", 2).size());
    }

    @Test
    void lastResortIsTheDomainDefaultListWhenRankingIsEmpty() {
        given(rankings.findByPlatformWithContent(anyString())).willReturn(List.of());
        given(workApiService.getWorks(eq(Domain.WEBNOVEL), isNull(), isNull(), any(Pageable.class)))
                .willReturn(PageResponse.<WorkSummaryDTO>builder().content(List.of(dto(7L), dto(8L))).build());

        List<WorkSummaryDTO> works = provider.byTab("webnovel", 20);

        assertEquals(List.of(7L, 8L), works.stream().map(WorkSummaryDTO::getId).toList());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(workApiService).getWorks(eq(Domain.WEBNOVEL), isNull(), isNull(), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(20, pageable.getValue().getPageSize());
    }

    @Test
    void lastResortForAllTabHasNoDomainFilter() {
        given(rankings.findByPlatformWithContent(anyString())).willReturn(List.of());
        given(workApiService.getWorks(isNull(), isNull(), isNull(), any(Pageable.class)))
                .willReturn(PageResponse.<WorkSummaryDTO>builder().content(List.of(dto(1L))).build());

        assertEquals(List.of(1L), provider.byTab("all", 20).stream().map(WorkSummaryDTO::getId).toList());
    }

    @Test
    void unknownTabFallsThroughToTheDefaultListWithoutDomain() {
        given(workApiService.getWorks(isNull(), isNull(), isNull(), any(Pageable.class)))
                .willReturn(PageResponse.<WorkSummaryDTO>builder().content(List.of()).build());

        assertEquals(List.of(), provider.byTab("nope", 20));
        verify(rankings, never()).findByPlatformWithContent(anyString());
    }

    @Test
    void nullContentPageIsEmptyList() {
        given(rankings.findByPlatformWithContent(anyString())).willReturn(List.of());
        given(workApiService.getWorks(any(), any(), any(), any(Pageable.class)))
                .willReturn(PageResponse.<WorkSummaryDTO>builder().build());   // content 가 null 인 페이지

        assertEquals(List.of(), provider.byTab("game", 20));
    }

    @Test
    void filtersAreNeverPassed() {
        given(rankings.findByPlatformWithContent(anyString())).willReturn(List.of());
        given(workApiService.getWorks(any(), any(), any(), any(Pageable.class)))
                .willReturn(PageResponse.<WorkSummaryDTO>builder().content(List.of()).build());

        provider.byTab("movie", 20);

        ArgumentCaptor<WorkFilters> filters = ArgumentCaptor.forClass(WorkFilters.class);
        verify(workApiService).getWorks(eq(Domain.MOVIE), isNull(), filters.capture(), any(Pageable.class));
        assertNull(filters.getValue(), "기본 목록 첫 쪽이므로 필터를 주지 않는다");
    }
}
