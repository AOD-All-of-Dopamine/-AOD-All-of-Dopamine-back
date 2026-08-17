package com.example.AOD.api.controller;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.review.ReviewRequest;
import com.example.AOD.api.dto.review.ReviewResponseDTO;
import com.example.AOD.api.service.ReviewService;
import com.example.AOD.security.JwtTokenProvider;
import com.example.AOD.support.RestDocsTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ReviewControllerDocsTest extends RestDocsTestSupport {

    @MockBean
    private ReviewService reviewService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getReviews() throws Exception {
        // given
        ReviewResponseDTO mockDto = ReviewResponseDTO.builder()
                .reviewId(1L)
                .contentId(10L)
                .contentTitle("스파이더맨")
                .userId(100L)
                .username("testUser")
                .rating(4.5)
                .title("재미있게 봤어요!")
                .content("액션 훌륭합니다.")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isMyReview(false)
                .build();

        PageResponse mockResponse = new PageResponse(Collections.singletonList(mockDto), 0, 20, 1, 1, true, true);
        given(reviewService.getReviewsByContentId(anyLong(), any(), any())).willReturn(mockResponse);
        given(jwtTokenProvider.getUsername(anyString())).willReturn("testUser");

        // when & then
        mockMvc.perform(get("/api/works/{contentId}/reviews", 10L)
                        .header("Authorization", "Bearer mock-token")
                        .param("page", "0")
                        .param("size", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("reviews-get",
                        pathParameters(
                                parameterWithName("contentId").description("작품 ID")
                        ),
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰 (Bearer 타입) - 옵션").optional()
                        ),
                        queryParameters(
                                parameterWithName("page").description("조회할 페이지 번호 (0부터 시작)").optional(),
                                parameterWithName("size").description("페이지당 노출 건 수").optional()
                        ),
                        responseFields(
                                fieldWithPath("content[].reviewId").description("리뷰 고유 ID"),
                                fieldWithPath("content[].contentId").description("작품 고유 ID"),
                                fieldWithPath("content[].contentTitle").description("작품 제목"),
                                fieldWithPath("content[].userId").description("작성자 유저 ID"),
                                fieldWithPath("content[].username").description("작성자명 (닉네임)"),
                                fieldWithPath("content[].rating").description("부여된 별점 (0.0~5.0)"),
                                fieldWithPath("content[].title").description("리뷰 제목"),
                                fieldWithPath("content[].content").description("리뷰 본문"),
                                fieldWithPath("content[].createdAt").description("최초 생성일시"),
                                fieldWithPath("content[].updatedAt").description("마지막 수정일시"),
                                fieldWithPath("content[].isMyReview").description("조회 요청자가 본인인지 여부"),
                                fieldWithPath("page").description("현재 페이지 번호"),
                                fieldWithPath("size").description("페이지 사이즈"),
                                fieldWithPath("totalElements").description("전체 리뷰 수"),
                                fieldWithPath("totalPages").description("전체 페이지 수"),
                                fieldWithPath("first").description("첫 페이지 여부"),
                                fieldWithPath("last").description("마지막 페이지 여부")
                        )
                ));
    }

    @Test
    void createReview() throws Exception {
        // given
        ReviewRequest request = new ReviewRequest(5.0, "최고의 명작", "인생 영화입니다!");
        ReviewResponseDTO mockDto = ReviewResponseDTO.builder()
                .reviewId(2L)
                .contentId(10L)
                .contentTitle("스파이더맨")
                .userId(100L)
                .username("testUser")
                .rating(5.0)
                .title("최고의 명작")
                .content("인생 영화입니다!")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isMyReview(true)
                .build();

        given(jwtTokenProvider.getUsername(anyString())).willReturn("testUser");
        given(reviewService.createReview(anyLong(), anyString(), any())).willReturn(mockDto);

        // when & then
        mockMvc.perform(post("/api/works/{contentId}/reviews", 10L)
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("reviews-create",
                        pathParameters(
                                parameterWithName("contentId").description("작품 ID")
                        ),
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰 (필수)")
                        ),
                        requestFields(
                                fieldWithPath("rating").description("등록할 별점 (0.0~5.0)"),
                                fieldWithPath("title").description("리뷰 제목"),
                                fieldWithPath("content").description("리뷰 내용 본문")
                        ),
                        responseFields(
                                fieldWithPath("reviewId").description("생성된 리뷰 고유 ID"),
                                fieldWithPath("contentId").description("작품 고유 ID"),
                                fieldWithPath("contentTitle").description("작품 제목"),
                                fieldWithPath("userId").description("작성자 유저 ID"),
                                fieldWithPath("username").description("작성자명"),
                                fieldWithPath("rating").description("등록된 별점"),
                                fieldWithPath("title").description("등록된 제목"),
                                fieldWithPath("content").description("등록된 본문"),
                                fieldWithPath("createdAt").description("생성일시"),
                                fieldWithPath("updatedAt").description("수정일시"),
                                fieldWithPath("isMyReview").description("본인 작성 여부 (항상 true)")
                        )
                ));
    }

    @Test
    void updateReview() throws Exception {
        // given
        ReviewRequest request = new ReviewRequest(4.0, "생각보다 괜찮네요", "킬링타임으로 좋습니다.");
        ReviewResponseDTO mockDto = ReviewResponseDTO.builder()
                .reviewId(2L)
                .contentId(10L)
                .contentTitle("스파이더맨")
                .userId(100L)
                .username("testUser")
                .rating(4.0)
                .title("생각보다 괜찮네요")
                .content("킬링타임으로 좋습니다.")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isMyReview(true)
                .build();

        given(jwtTokenProvider.getUsername(anyString())).willReturn("testUser");
        given(reviewService.updateReview(anyLong(), anyString(), any())).willReturn(mockDto);

        // when & then
        mockMvc.perform(RestDocumentationRequestBuilders.put("/api/reviews/{reviewId}", 2L)
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("reviews-update",
                        pathParameters(
                                parameterWithName("reviewId").description("수정할 리뷰 ID")
                        ),
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰 (필수)")
                        ),
                        requestFields(
                                fieldWithPath("rating").description("수정할 별점 (0.0~5.0)"),
                                fieldWithPath("title").description("수정할 제목"),
                                fieldWithPath("content").description("수정할 본문")
                        ),
                        responseFields(
                                fieldWithPath("reviewId").description("수정된 리뷰 고유 ID"),
                                fieldWithPath("contentId").description("작품 고유 ID"),
                                fieldWithPath("contentTitle").description("작품 제목"),
                                fieldWithPath("userId").description("작성자 유저 ID"),
                                fieldWithPath("username").description("작성자명"),
                                fieldWithPath("rating").description("별점"),
                                fieldWithPath("title").description("제목"),
                                fieldWithPath("content").description("본문"),
                                fieldWithPath("createdAt").description("최초 생성일시"),
                                fieldWithPath("updatedAt").description("수정일시 (갱신됨)"),
                                fieldWithPath("isMyReview").description("본인 작성 여부")
                        )
                ));
    }

    @Test
    void deleteReview() throws Exception {
        // given
        given(jwtTokenProvider.getUsername(anyString())).willReturn("testUser");

        // when & then
        mockMvc.perform(RestDocumentationRequestBuilders.delete("/api/reviews/{reviewId}", 2L)
                        .header("Authorization", "Bearer mock-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("reviews-delete",
                        pathParameters(
                                parameterWithName("reviewId").description("삭제할 리뷰 ID")
                        ),
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰 (필수)")
                        ),
                        responseFields(
                                fieldWithPath("message").description("결과 메시지")
                        )
                ));
    }

    @Test
    void getMyReviews() throws Exception {
        // given
        ReviewResponseDTO mockDto = ReviewResponseDTO.builder()
                .reviewId(2L)
                .contentId(10L)
                .contentTitle("스파이더맨")
                .userId(100L)
                .username("testUser")
                .rating(5.0)
                .title("명작입니다.")
                .content("정말 재미있게 봤어요.")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isMyReview(true)
                .build();

        PageResponse mockResponse = new PageResponse(Collections.singletonList(mockDto), 0, 20, 1, 1, true, true);
        given(jwtTokenProvider.getUsername(anyString())).willReturn("testUser");
        given(reviewService.getMyReviews(anyString(), any())).willReturn(mockResponse);

        // when & then
        mockMvc.perform(get("/api/my/reviews")
                        .header("Authorization", "Bearer mock-token")
                        .param("page", "0")
                        .param("size", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("reviews-my",
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰 (필수)")
                        ),
                        queryParameters(
                                parameterWithName("page").description("페이지 번호 (0부터 시작)").optional(),
                                parameterWithName("size").description("페이지 크기").optional()
                        ),
                        responseFields(
                                fieldWithPath("content[].reviewId").description("리뷰 ID"),
                                fieldWithPath("content[].contentId").description("해당 작품 ID"),
                                fieldWithPath("content[].contentTitle").description("해당 작품 제목"),
                                fieldWithPath("content[].userId").description("작성자 ID"),
                                fieldWithPath("content[].username").description("닉네임"),
                                fieldWithPath("content[].rating").description("내가 준 별점"),
                                fieldWithPath("content[].title").description("내가 쓴 제목"),
                                fieldWithPath("content[].content").description("내가 쓴 내용"),
                                fieldWithPath("content[].createdAt").description("작성일시"),
                                fieldWithPath("content[].updatedAt").description("수정일시"),
                                fieldWithPath("content[].isMyReview").description("본인 여부 (항상 true)"),
                                fieldWithPath("page").description("데이터 페이지 번호"),
                                fieldWithPath("size").description("페이지 크기"),
                                fieldWithPath("totalElements").description("전체 데이터 수"),
                                fieldWithPath("totalPages").description("전체 페이지 수"),
                                fieldWithPath("first").description("첫 페이지 여부"),
                                fieldWithPath("last").description("마지막 페이지 여부")
                        )
                ));
    }
}
