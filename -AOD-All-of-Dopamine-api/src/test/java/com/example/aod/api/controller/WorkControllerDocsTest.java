package com.example.AOD.api.controller;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.WorkResponseDTO;
import com.example.AOD.api.service.WorkApiService;
import com.example.AOD.support.RestDocsTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class WorkControllerDocsTest extends RestDocsTestSupport {

    @MockBean
    private WorkApiService workApiService;

    @Test
    void getWorksList() throws Exception {
        // given: 빈 리스트를 응답하는 Mock 객체 설정 (DB 연결 없이 문서화만 테스트)
        PageResponse mockResponse = new PageResponse(Collections.emptyList(), 0, 20, 0, 0, true, true);
        given(workApiService.getWorks(any(), any(), any(), any())).willReturn(mockResponse);

        // when & then
        mockMvc.perform(get("/api/works")
                        .param("domain", "GAME")
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
}
