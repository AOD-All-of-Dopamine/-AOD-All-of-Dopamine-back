package com.example.AOD.ranking.controller;

import com.example.AOD.ranking.dto.RankingResponse;
import com.example.AOD.ranking.mapper.RankingMapper;
import com.example.AOD.ranking.service.RankingService;
import com.example.AOD.support.RestDocsTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class RankingControllerDocsTest extends RestDocsTestSupport {

    @MockBean
    private com.example.AOD.ranking.service.RankingHistoryStore historyStore;

    @MockBean
    private RankingService rankingService;

    @MockBean
    private RankingMapper rankingMapper;

    @Test
    void getAllRankings() throws Exception {
        // given
        RankingResponse mockResponse = new RankingResponse();
        mockResponse.setId(100L);
        mockResponse.setContentId(5L);
        mockResponse.setTitle("나혼자만 레벨업");
        mockResponse.setRanking(1);
        mockResponse.setPlatform("NaverWebtoon");
        mockResponse.setThumbnailUrl("http://example.com/thumb.png");
        mockResponse.setWatchProviders(Arrays.asList("NaverSeries", "KakaoPage"));

        RankingResponse.ContentInfo contentInfo = new RankingResponse.ContentInfo();
        contentInfo.setContentId(5L);
        contentInfo.setDomain("WEBTOON");
        contentInfo.setMasterTitle("나혼자만 레벨업");
        contentInfo.setPosterImageUrl("http://example.com/poster.png");
        mockResponse.setContent(contentInfo);

        given(rankingService.getAllRankings()).willReturn(Collections.emptyList()); // returns ignored
        given(rankingMapper.toResponseList(any(), any())).willReturn(Collections.singletonList(mockResponse));

        // when & then
        mockMvc.perform(get("/api/rankings/all")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("rankings-all",
                        responseFields(
                                fieldWithPath("[].id").description("랭킹 엔트리 고유 ID"),
                                fieldWithPath("[].contentId").description("매핑된 내부 작품 ID (null 가능)"),
                                fieldWithPath("[].title").description("작품 제목"),
                                fieldWithPath("[].ranking").description("순위 (1부터 시작)"),
                                fieldWithPath("[].platform").description("플랫폼 (NaverWebtoon, Steam 등)"),
                                fieldWithPath("[].thumbnailUrl").description("썸네일 이미지 URL"),
                                fieldWithPath("[].watchProviders").description("OTT 플랫폼 목록 (문자열 배열)"),
                                fieldWithPath("[].portraitImageUrl").description("게임 세로 표지 (없으면 null)").optional(),
                                fieldWithPath("[].ratingScore").description("순위를 받을 때의 평가 — 게임: 긍정 비율(0~1) · 영화/시리즈: TMDB 평점").optional(),
                                fieldWithPath("[].ratingCount").description("게임: 리뷰 수 · 영화/시리즈: 투표 수").optional(),
                                fieldWithPath("[].ratingLabel").description("게임: Steam 판정(영문)").optional(),
                                fieldWithPath("[].previousRanking").description("비교 기준일 순위 — 기준일 기록에 없으면 null(= NEW, rankBaseDate 가 있을 때만)").optional(),
                                fieldWithPath("[].rankBaseDate").description("비교 기준일 yyyy-MM-dd — null 이면 비교할 기록 없음(변동 표시 안 함). 네이버웹툰은 7일 전 같은 요일").optional(),
                                fieldWithPath("[].content").description("내부 작품 매핑 정보 (null 가능)").optional(),
                                fieldWithPath("[].content.contentId").description("내부 작품 ID").optional(),
                                fieldWithPath("[].content.domain").description("작품 도메인").optional(),
                                fieldWithPath("[].content.masterTitle").description("마스터 글로벌 제목").optional(),
                                fieldWithPath("[].content.posterImageUrl").description("포스터 URL").optional()
                        )
                ));
    }

    @Test
    void getRankingsByPlatform() throws Exception {
        // given
        RankingResponse mockResponse = new RankingResponse();
        mockResponse.setId(200L);
        mockResponse.setContentId(15L);
        mockResponse.setTitle("오징어 게임");
        mockResponse.setRanking(1);
        mockResponse.setPlatform("Netflix");
        mockResponse.setThumbnailUrl("http://example.com/thumb_squid.png");

        given(rankingService.getRankingsByPlatform(anyString())).willReturn(Collections.emptyList());
        given(rankingMapper.toResponseList(any(), any())).willReturn(Collections.singletonList(mockResponse));

        // when & then
        mockMvc.perform(get("/api/rankings/{platform}", "Netflix")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("rankings-by-platform",
                        pathParameters(
                                parameterWithName("platform").description("플랫폼 이름 (예: Netflix, Steam, TMDB_MOVIE)")
                        ),
                        responseFields(
                                fieldWithPath("[].id").description("랭킹 엔트리 고유 ID"),
                                fieldWithPath("[].contentId").description("매핑된 내부 작품 ID"),
                                fieldWithPath("[].title").description("작품 제목"),
                                fieldWithPath("[].ranking").description("순위"),
                                fieldWithPath("[].platform").description("조회한 플랫폼 이름"),
                                fieldWithPath("[].thumbnailUrl").description("썸네일 이미지 URL"),
                                fieldWithPath("[].watchProviders").description("스트리밍 지원 플랫폼 목록 (null 가능)").optional(),
                                fieldWithPath("[].portraitImageUrl").description("게임 세로 표지 (없으면 null)").optional(),
                                fieldWithPath("[].ratingScore").description("순위를 받을 때의 평가 — 게임: 긍정 비율(0~1) · 영화/시리즈: TMDB 평점").optional(),
                                fieldWithPath("[].ratingCount").description("게임: 리뷰 수 · 영화/시리즈: 투표 수").optional(),
                                fieldWithPath("[].ratingLabel").description("게임: Steam 판정(영문)").optional(),
                                fieldWithPath("[].previousRanking").description("비교 기준일 순위 — 기준일 기록에 없으면 null(= NEW, rankBaseDate 가 있을 때만)").optional(),
                                fieldWithPath("[].rankBaseDate").description("비교 기준일 yyyy-MM-dd — null 이면 비교할 기록 없음(변동 표시 안 함). 네이버웹툰은 7일 전 같은 요일").optional(),
                                fieldWithPath("[].content").description("내부 매핑 정보").optional()
                        )
                ));
    }

    @Test
    void getRankingsByDomain() throws Exception {
        // given
        RankingResponse mockResponse = new RankingResponse();
        mockResponse.setId(300L);
        mockResponse.setContentId(25L);
        mockResponse.setTitle("리그 오브 레전드");
        mockResponse.setRanking(1);
        mockResponse.setPlatform("PC");
        mockResponse.setThumbnailUrl("http://example.com/thumb_lol.png");

        given(rankingService.getRankingsByDomain(anyString())).willReturn(Collections.emptyList());
        given(rankingMapper.toResponseList(any(), any())).willReturn(Collections.singletonList(mockResponse));

        // when & then
        mockMvc.perform(get("/api/rankings/domain/{domain}", "GAME")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("rankings-by-domain",
                        pathParameters(
                                parameterWithName("domain").description("도메인 종류 (예: MOVIE, TV, GAME, WEBTOON, WEBNOVEL)")
                        ),
                        responseFields(
                                fieldWithPath("[].id").description("랭킹 고유 ID"),
                                fieldWithPath("[].contentId").description("매핑된 작품 ID"),
                                fieldWithPath("[].title").description("작품 제목"),
                                fieldWithPath("[].ranking").description("순위"),
                                fieldWithPath("[].platform").description("플랫폼"),
                                fieldWithPath("[].thumbnailUrl").description("썸네일 이미지 URL"),
                                fieldWithPath("[].watchProviders").description("스트리밍/구매 플랫폼 목록").optional(),
                                fieldWithPath("[].portraitImageUrl").description("게임 세로 표지 (없으면 null)").optional(),
                                fieldWithPath("[].ratingScore").description("순위를 받을 때의 평가 — 게임: 긍정 비율(0~1) · 영화/시리즈: TMDB 평점").optional(),
                                fieldWithPath("[].ratingCount").description("게임: 리뷰 수 · 영화/시리즈: 투표 수").optional(),
                                fieldWithPath("[].ratingLabel").description("게임: Steam 판정(영문)").optional(),
                                fieldWithPath("[].previousRanking").description("비교 기준일 순위 — 기준일 기록에 없으면 null(= NEW, rankBaseDate 가 있을 때만)").optional(),
                                fieldWithPath("[].rankBaseDate").description("비교 기준일 yyyy-MM-dd — null 이면 비교할 기록 없음(변동 표시 안 함). 네이버웹툰은 7일 전 같은 요일").optional(),
                                fieldWithPath("[].content").description("내부 매핑 정보").optional()
                        )
                ));
    }
}
