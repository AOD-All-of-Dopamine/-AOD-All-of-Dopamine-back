package com.example.AOD.api.controller;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.api.service.BookmarkService;
import com.example.AOD.api.service.LikeService;
import com.example.AOD.security.JwtTokenProvider;
import com.example.AOD.support.RestDocsTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class InteractionControllerDocsTest extends RestDocsTestSupport {

    @MockBean
    private LikeService likeService;

    @MockBean
    private BookmarkService bookmarkService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void toggleLike() throws Exception {
        // given
        given(jwtTokenProvider.getUsername(anyString())).willReturn("testUser");
        given(likeService.toggleLike(anyLong(), anyString())).willReturn(Map.of(
                "contentId", 10L,
                "likeCount", 15L,
                "dislikeCount", 2L,
                "userLikeType", "LIKE",
                "message", "좋아요!"
        ));

        // when & then
        mockMvc.perform(post("/api/works/{contentId}/like", 10L)
                        .header("Authorization", "Bearer mock-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("interactions-like",
                        pathParameters(
                                parameterWithName("contentId").description("작품 ID")
                        ),
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰")
                        ),
                        responseFields(
                                fieldWithPath("contentId").description("작품 ID"),
                                fieldWithPath("likeCount").description("전체 좋아요 수"),
                                fieldWithPath("dislikeCount").description("전체 싫어요 수"),
                                fieldWithPath("userLikeType").description("현재 유저의 타입 (LIKE/DISLIKE/NONE)"),
                                fieldWithPath("message").description("결과 메시지")
                        )
                ));
    }

    @Test
    void toggleDislike() throws Exception {
        // given
        given(jwtTokenProvider.getUsername(anyString())).willReturn("testUser");
        given(likeService.toggleDislike(anyLong(), anyString())).willReturn(Map.of(
                "contentId", 10L,
                "likeCount", 14L,
                "dislikeCount", 3L,
                "userLikeType", "DISLIKE",
                "message", "싫어요"
        ));

        // when & then
        mockMvc.perform(post("/api/works/{contentId}/dislike", 10L)
                        .header("Authorization", "Bearer mock-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("interactions-dislike",
                        pathParameters(
                                parameterWithName("contentId").description("작품 ID")
                        ),
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰")
                        ),
                        responseFields(
                                fieldWithPath("contentId").description("작품 ID"),
                                fieldWithPath("likeCount").description("전체 좋아요 수"),
                                fieldWithPath("dislikeCount").description("전체 싫어요 수"),
                                fieldWithPath("userLikeType").description("현재 유저의 타입 (LIKE/DISLIKE/NONE)"),
                                fieldWithPath("message").description("결과 메시지")
                        )
                ));
    }

    @Test
    void toggleBookmark() throws Exception {
        // given
        given(jwtTokenProvider.getUsername(anyString())).willReturn("testUser");
        given(bookmarkService.toggleBookmark(anyLong(), anyString())).willReturn(Map.of(
                "contentId", 10L,
                "bookmarked", true,
                "message", "북마크에 추가되었습니다."
        ));

        // when & then
        mockMvc.perform(post("/api/works/{contentId}/bookmark", 10L)
                        .header("Authorization", "Bearer mock-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("interactions-bookmark",
                        pathParameters(
                                parameterWithName("contentId").description("작품 ID")
                        ),
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰")
                        ),
                        responseFields(
                                fieldWithPath("contentId").description("작품 ID"),
                                fieldWithPath("bookmarked").description("북마크 여부 (true/false)"),
                                fieldWithPath("message").description("결과 메시지")
                        )
                ));
    }

    @Test
    void getLikeStats() throws Exception {
        // given
        given(likeService.getLikeStats(anyLong(), any())).willReturn(Map.of(
                "contentId", 10L,
                "likeCount", 100L,
                "dislikeCount", 5L,
                "userLikeType", "NONE"
        ));

        // when & then
        mockMvc.perform(get("/api/works/{contentId}/likes", 10L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("interactions-likes-stats",
                        pathParameters(
                                parameterWithName("contentId").description("작품 ID")
                        ),
                        responseFields(
                                fieldWithPath("contentId").description("작품 ID"),
                                fieldWithPath("likeCount").description("전체 좋아요 수"),
                                fieldWithPath("dislikeCount").description("전체 싫어요 수"),
                                fieldWithPath("userLikeType").description("현재 유저의 타입 (LIKE/DISLIKE/NONE)")
                        )
                ));
    }

    @Test
    void getMyBookmarks() throws Exception {
        // given
        WorkSummaryDTO mockSummary = WorkSummaryDTO.builder()
                .id(10L)
                .title("기생충")
                .thumbnail("http://example.com/thumbnail.png")
                .domain("MOVIE")
                .releaseDate("2019-05-30")
                .score(4.8)
                .build();
        
        PageResponse mockResponse = new PageResponse(Collections.singletonList(mockSummary), 0, 20, 1, 1, true, true);
        given(jwtTokenProvider.getUsername(anyString())).willReturn("testUser");
        given(bookmarkService.getMyBookmarks(anyString(), any())).willReturn(mockResponse);

        // when & then
        mockMvc.perform(get("/api/my/bookmarks")
                        .header("Authorization", "Bearer mock-token")
                        .param("page", "0")
                        .param("size", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("interactions-my-bookmarks",
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰")
                        ),
                        queryParameters(
                                parameterWithName("page").description("페이지 번호").optional(),
                                parameterWithName("size").description("사이즈").optional()
                        ),
                        responseFields(
                                fieldWithPath("content[].id").description("작품 ID"),
                                fieldWithPath("content[].title").description("제목"),
                                fieldWithPath("content[].thumbnail").description("썸네일 이미지 URL"),
                                fieldWithPath("content[].domain").description("도메인 (MOVIE 등)"),
                                fieldWithPath("content[].releaseDate").description("개봉일"),
                                fieldWithPath("content[].score").description("평점"),
                                fieldWithPath("content[].rank").description("작품 순위 (null 가능)").optional(),
                                fieldWithPath("content[].rankChange").description("순위 변동량 (null 가능)").optional(),
                                fieldWithPath("content[].genres").description("장르 목록 (null 가능)").optional(),
                                fieldWithPath("content[].platforms").description("플랫폼 목록 (null 가능)").optional(),
                                fieldWithPath("content[].creator").description("도메인별 대표 제작자 (null 가능)").optional(),
                                fieldWithPath("content[].weekday").description("웹툰 연재 요일 (null 가능)").optional(),
                                fieldWithPath("content[].status").description("웹툰 연재 상태 (null 가능)").optional(),
                                fieldWithPath("content[].ageRating").description("연령 등급 (null 가능)").optional(),
                                fieldWithPath("content[].steamReviewDesc").description("Steam 리뷰 요약 (null 가능)").optional(),
                                fieldWithPath("content[].steamPositivePct").description("Steam 긍정 비율 % (null 가능)").optional(),
                                fieldWithPath("content[].externalRating").description("TMDB 평점 (null 가능)").optional(),
                                fieldWithPath("page").description("데이터 페이지 번호"),
                                fieldWithPath("size").description("페이지 크기"),
                                fieldWithPath("totalElements").description("전체 데이터 수"),
                                fieldWithPath("totalPages").description("전체 페이지 수"),
                                fieldWithPath("first").description("첫 페이지 여부"),
                                fieldWithPath("last").description("마지막 페이지 여부")
                        )
                ));
    }

    @Test
    void getMyLikes() throws Exception {
        // given
        WorkSummaryDTO mockSummary = WorkSummaryDTO.builder()
                .id(10L)
                .title("아이언맨")
                .thumbnail("http://example.com/ironman.png")
                .domain("MOVIE")
                .releaseDate("2008-04-30")
                .score(4.5)
                .build();
        
        PageResponse mockResponse = new PageResponse(Collections.singletonList(mockSummary), 0, 20, 1, 1, true, true);
        given(jwtTokenProvider.getUsername(anyString())).willReturn("testUser");
        given(likeService.getMyLikes(anyString(), any())).willReturn(mockResponse);

        // when & then
        mockMvc.perform(get("/api/my/likes")
                        .header("Authorization", "Bearer mock-token")
                        .param("page", "0")
                        .param("size", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("interactions-my-likes",
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰")
                        ),
                        queryParameters(
                                parameterWithName("page").description("페이지 번호").optional(),
                                parameterWithName("size").description("사이즈").optional()
                        ),
                        responseFields(
                                fieldWithPath("content[].id").description("작품 ID"),
                                fieldWithPath("content[].title").description("제목"),
                                fieldWithPath("content[].thumbnail").description("썸네일 이미지 URL"),
                                fieldWithPath("content[].domain").description("도메인"),
                                fieldWithPath("content[].releaseDate").description("개봉일"),
                                fieldWithPath("content[].score").description("평점"),
                                fieldWithPath("content[].rank").description("작품 순위 (null 가능)").optional(),
                                fieldWithPath("content[].rankChange").description("순위 변동량 (null 가능)").optional(),
                                fieldWithPath("content[].genres").description("장르 목록 (null 가능)").optional(),
                                fieldWithPath("content[].platforms").description("플랫폼 목록 (null 가능)").optional(),
                                fieldWithPath("content[].creator").description("도메인별 대표 제작자 (null 가능)").optional(),
                                fieldWithPath("content[].weekday").description("웹툰 연재 요일 (null 가능)").optional(),
                                fieldWithPath("content[].status").description("웹툰 연재 상태 (null 가능)").optional(),
                                fieldWithPath("content[].ageRating").description("연령 등급 (null 가능)").optional(),
                                fieldWithPath("content[].steamReviewDesc").description("Steam 리뷰 요약 (null 가능)").optional(),
                                fieldWithPath("content[].steamPositivePct").description("Steam 긍정 비율 % (null 가능)").optional(),
                                fieldWithPath("content[].externalRating").description("TMDB 평점 (null 가능)").optional(),
                                fieldWithPath("page").description("데이터 페이지 번호"),
                                fieldWithPath("size").description("페이지 크기"),
                                fieldWithPath("totalElements").description("전체 데이터 수"),
                                fieldWithPath("totalPages").description("전체 페이지 수"),
                                fieldWithPath("first").description("첫 페이지 여부"),
                                fieldWithPath("last").description("마지막 페이지 여부")
                        )
                ));
    }

    @Test
    void getBookmarkStatus() throws Exception {
        // given
        given(bookmarkService.getBookmarkStatus(anyLong(), any())).willReturn(Map.of(
                "contentId", 10L,
                "bookmarked", true
        ));

        // when & then
        mockMvc.perform(get("/api/works/{contentId}/bookmark", 10L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("interactions-bookmark-status",
                        pathParameters(
                                parameterWithName("contentId").description("작품 ID")
                        ),
                        responseFields(
                                fieldWithPath("contentId").description("작품 ID"),
                                fieldWithPath("bookmarked").description("북마크 여부 (true/false)")
                        )
                ));
    }
}
