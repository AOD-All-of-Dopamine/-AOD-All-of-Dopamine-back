package com.example.AOD.api.controller;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.WorkResponseDTO;
import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.api.featured.FeaturedWorkDTO;
import com.example.AOD.api.featured.FeaturedWorkService;
import com.example.AOD.api.service.WorkApiService;
import com.example.AOD.support.RestDocsTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class WorkControllerDocsTest extends RestDocsTestSupport {

    @MockBean
    private WorkApiService workApiService;

    @MockBean
    private FeaturedWorkService featuredWorkService;

    @Test
    void getWorksList() throws Exception {
        // given: 빈 리스트를 응답하는 Mock 객체 설정 (DB 연결 없이 문서화만 테스트)
        PageResponse mockResponse = new PageResponse(Collections.emptyList(), 0, 20, 0, 0, true, true);
        given(workApiService.getWorks(any(), any(), any(), any())).willReturn(mockResponse);

        // when & then
        mockMvc.perform(get("/api/works")
                        .param("domain", "GAME")
                        .param("reviewCountMin", "100")
                        .param("page", "0")
                        .param("size", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("works-get-all", // 스니펫 폴더명: build/generated-snippets/works-get-all
                        queryParameters(
                                parameterWithName("domain").description("검색할 도메인 (예: GAME, MOVIE, WEBTOON)").optional(),
                                parameterWithName("keyword").description("검색어").optional(),
                                parameterWithName("platforms").description("플랫폼 필터 (콤마로 구분)").optional(),
                                parameterWithName("genres").description("장르 필터 (콤마로 구분)").optional(),
                                parameterWithName("reviewCountMin").description("게임 전용: Steam 리뷰 총수 하한 (게임 외 도메인과 함께 보내면 0건)").optional(),
                                parameterWithName("page").description("조회할 페이지 번호 (0부터 시작)").optional(),
                                parameterWithName("size").description("페이지당 노출 건 수").optional(),
                                parameterWithName("sortBy").description("정렬 기준 필드 (기본: masterTitle)").optional(),
                                parameterWithName("sortDirection").description("정렬 방식 (asc / desc)").optional()
                        ),
                        responseFields(
                                fieldWithPath("content").description("조회된 작품 데이터 배열 (요청 조건에 일치하는 항목)"),
                                fieldWithPath("page").description("현재 데이터 페이지 번호"),
                                fieldWithPath("size").description("요청한 페이지 사이즈"),
                                fieldWithPath("totalElements").description("검색된 전체 작품 수"),
                                fieldWithPath("totalPages").description("검색된 전체 페이지 수"),
                                fieldWithPath("first").description("현재 페이지가 첫 페이지인지 여부"),
                                fieldWithPath("last").description("현재 페이지가 마지막 페이지인지 여부")
                        )
                ));
    }

    @Test
    void getWorkDetail() throws Exception {
        // given
        WorkResponseDTO mockResponse = WorkResponseDTO.builder()
                .id(1L)
                .domain("MOVIE")
                .title("기생충")
                .originalTitle("Parasite")
                .releaseDate("2019-05-30")
                .thumbnail("http://example.com/parasite.png")
                .synopsis("전원백수로 살 길 막막하지만 사이는 좋은 기택 가족...")
                .score(4.9)
                .domainInfo(java.util.Map.of("director", "봉준호", "cast", "송강호, 이선균"))
                .platformInfo(java.util.Map.of("Netflix", java.util.Map.of("url", "http://netflix.com")))
                .build();

        given(workApiService.getWorkDetail(anyLong())).willReturn(mockResponse);

        // when & then
        mockMvc.perform(get("/api/works/{id}", 1L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("works-get-detail",
                        pathParameters(
                                parameterWithName("id").description("작품 ID")
                        ),
                        responseFields(
                                fieldWithPath("id").description("작품 ID"),
                                fieldWithPath("domain").description("작품 분야 (MOVIE, GAME, WEBTOON 등)"),
                                fieldWithPath("title").description("작품 제목"),
                                fieldWithPath("originalTitle").description("원제 (null 가능)").optional(),
                                fieldWithPath("releaseDate").description("개봉일/출시일 (yyyy-MM-dd)").optional(),
                                fieldWithPath("thumbnail").description("썸네일 이미지 URL").optional(),
                                fieldWithPath("synopsis").description("시놉시스 (줄거리)").optional(),
                                fieldWithPath("score").description("내부 평점"),
                                fieldWithPath("domainInfo").description("도메인별 특화 추가 정보 (감독, 개발사 등)").optional(),
                                fieldWithPath("domainInfo.director").description("감독 정보 예시").optional(),
                                fieldWithPath("domainInfo.cast").description("배우 정보 예시").optional(),
                                fieldWithPath("platformInfo").description("플랫폼별 부가 정보 (URL 등)").optional(),
                                fieldWithPath("platformInfo.Netflix").description("넷플릭스 부가 정보 예시").optional(),
                                fieldWithPath("platformInfo.Netflix.url").description("넷플릭스 링크").optional()
                        )
                ));
    }

    @Test
    void getGenres() throws Exception {
        // given
        given(workApiService.getAvailableGenres(any())).willReturn(java.util.Arrays.asList("액션", "코미디", "드라마"));

        // when & then
        mockMvc.perform(get("/api/works/genres")
                        .param("domain", "MOVIE")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("works-get-genres",
                        queryParameters(
                                parameterWithName("domain").description("필터링할 도메인 (선택)").optional()
                        ),
                        responseFields(
                                fieldWithPath("[]").description("사용 가능한 장르 이름 목록 (문자열 배열)")
                        )
                ));
    }

    @Test
    void getPlatforms() throws Exception {
        // given
        given(workApiService.getAvailablePlatforms(any())).willReturn(java.util.Arrays.asList("Steam", "Netflix", "NaverWebtoon"));

        // when & then
        mockMvc.perform(get("/api/works/platforms")
                        .param("domain", "GAME")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("works-get-platforms",
                        queryParameters(
                                parameterWithName("domain").description("필터링할 도메인 (선택)").optional()
                        ),
                        responseFields(
                                fieldWithPath("[]").description("사용 가능한 플랫폼 이름 목록 (문자열 배열)")
                        )
                ));
    }

    @Test
    void implLegacyRoutesToLegacyServicePath() throws Exception {
        // A/B 측정용 임시 파라미터 (troubleshooting/07 §8-1): impl=legacy → 구 쿼리 경로, 동적 경로는 호출되지 않는다
        PageResponse mockResponse = new PageResponse(Collections.emptyList(), 0, 20, 0, 0, true, true);
        given(workApiService.getWorksLegacy(any(), any(), any(), any())).willReturn(mockResponse);

        mockMvc.perform(get("/api/works")
                        .param("domain", "GAME")
                        .param("reviewCountMin", "100")
                        .param("impl", "legacy")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(workApiService).getWorksLegacy(any(), any(), any(), any());
        verify(workApiService, never()).getWorks(any(), any(), any(), any());
    }

    @Test
    void getFeaturedToday() throws Exception {
        // 2026-09-27 12:00 KST — 다음 05:00 KST(09-28)까지 17시간
        given(featuredWorkService.now()).willReturn(Instant.parse("2026-09-27T03:00:00Z"));
        WorkSummaryDTO work = new WorkSummaryDTO();
        work.setId(42L);
        work.setDomain("GAME");
        work.setTitle("Hades II");
        work.setThumbnail("http://example.com/hades2.jpg");
        work.setReleaseDate("2025-09-25");
        given(featuredWorkService.pick(LocalDate.of(2026, 9, 27))).willReturn(Optional.of(new FeaturedWorkDTO(
                "2026-09-27", work,
                new FeaturedWorkDTO.Reason("Steam", 8, "steam", 0.9431, 125_310, "Overwhelmingly Positive"))));

        mockMvc.perform(get("/api/works/featured-today").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=61200, public"))
                .andDo(document("works-featured-today",
                        responseFields(
                                fieldWithPath("date").description("오늘의 작품 날짜 (yyyy-MM-dd, 05:00 KST 에 바뀜)"),
                                subsectionWithPath("work").description("작품 요약 — 목록 조회의 content 항목과 같은 형식"),
                                fieldWithPath("reason").description("고른 근거 — 뽑을 때의 값이라 그날 안에서 바뀌지 않는다"),
                                fieldWithPath("reason.platform").description("랭킹 플랫폼 (Steam · TMDB_MOVIE · TMDB_TV)"),
                                fieldWithPath("reason.ranking").description("그날 랭킹 순위"),
                                fieldWithPath("reason.basis").description("평가 출처 (steam · tmdb)"),
                                fieldWithPath("reason.ratingScore").description("게임: 긍정 비율(0~1) · 영화/시리즈: TMDB 평점(10점)").optional(),
                                fieldWithPath("reason.ratingCount").description("게임: 리뷰 수 · 영화/시리즈: 투표 수").optional(),
                                fieldWithPath("reason.ratingLabel").description("게임: Steam 판정(영문, 예: Very Positive) · 그 밖 null").optional()
                        )
                ));
    }

    @Test
    void getFeaturedTodayEmptyIs204() throws Exception {
        given(featuredWorkService.now()).willReturn(Instant.parse("2026-09-27T03:00:00Z"));
        given(featuredWorkService.pick(any())).willReturn(Optional.empty());

        mockMvc.perform(get("/api/works/featured-today"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andDo(document("works-featured-today-empty"));
    }
}
