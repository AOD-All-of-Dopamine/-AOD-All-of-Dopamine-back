package com.example.AOD.api.controller;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.collection.CollectionCreateRequest;
import com.example.AOD.api.dto.collection.CollectionDetailDTO;
import com.example.AOD.api.dto.collection.CollectionItemAddRequest;
import com.example.AOD.api.dto.collection.CollectionItemDTO;
import com.example.AOD.api.dto.collection.CollectionItemUpdateRequest;
import com.example.AOD.api.dto.collection.CollectionOrderRequest;
import com.example.AOD.api.dto.collection.CollectionSummaryDTO;
import com.example.AOD.api.dto.collection.CollectionUpdateRequest;
import com.example.AOD.api.dto.collection.MyCollectionSummaryDTO;
import com.example.AOD.api.service.CollectionService;
import com.example.AOD.security.JwtTokenProvider;
import com.example.AOD.support.RestDocsTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class CollectionControllerDocsTest extends RestDocsTestSupport {

    @MockBean
    private CollectionService collectionService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private CollectionSummaryDTO sampleSummary() {
        return CollectionSummaryDTO.builder()
                .id(1L)
                .title("인생 무협 웹툰 모음")
                .description("정주행 보장. 무협 입문자에게 추천하는 컬렉션입니다.")
                .domain("WEBTOON")
                .tint("PINE")
                .visibility("PUBLIC")
                .likeCount(42)
                .viewCount(1024L)
                .itemCount(7L)
                .curatorNickname("curator")
                .coverPosters(List.of(
                        "http://example.com/poster1.jpg",
                        "http://example.com/poster2.jpg",
                        "http://example.com/poster3.jpg"))
                .likedByMe(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private CollectionItemDTO sampleItem() {
        return CollectionItemDTO.builder()
                .itemId(11L)
                .contentId(100L)
                .comment("이것부터 보세요. 최고의 도입부.")
                .position(0)
                .title("화산귀환")
                .posterUrl("http://example.com/poster1.jpg")
                .releaseDate("2019-04-24")
                .domain("WEBTOON")
                .score(4.8)
                .creator("비가")
                .genres(List.of("무협", "액션"))
                .platforms(List.of("NaverWebtoon"))
                .weekday("wed")
                .status("연재중")
                .ageRating("전체이용가")
                .steamReviewDesc("Very Positive")
                .steamPositivePct(93)
                .externalRating(8.5)
                .build();
    }

    @Test
    void getCollections() throws Exception {
        // given
        PageResponse<CollectionSummaryDTO> mockResponse = PageResponse.<CollectionSummaryDTO>builder()
                .content(Collections.singletonList(sampleSummary()))
                .page(0).size(20).totalElements(1L).totalPages(1).first(true).last(true)
                .build();
        given(jwtTokenProvider.getUsername(anyString())).willReturn("curator");
        given(collectionService.getPublicCollections(any(), any(), any(), anyInt(), anyInt()))
                .willReturn(mockResponse);

        // when & then
        mockMvc.perform(get("/api/collections")
                        .header("Authorization", "Bearer mock-token")
                        .param("sort", "popular")
                        .param("domain", "WEBTOON")
                        .param("page", "0")
                        .param("size", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("collections-get",
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰 (Bearer 타입) - 옵션, 있으면 likedByMe 계산").optional()
                        ),
                        queryParameters(
                                parameterWithName("sort").description("정렬: popular(기본, like_count desc) | latest(created_at desc)").optional(),
                                parameterWithName("domain").description("도메인 필터 (MOVIE/TV/GAME/WEBTOON/WEBNOVEL) - 옵션").optional(),
                                parameterWithName("page").description("조회할 페이지 번호 (0부터 시작)").optional(),
                                parameterWithName("size").description("페이지당 노출 건 수").optional()
                        ),
                        responseFields(
                                fieldWithPath("content[].id").description("컬렉션 고유 ID"),
                                fieldWithPath("content[].title").description("컬렉션 제목"),
                                fieldWithPath("content[].description").description("컬렉션 설명 (null 가능)").optional(),
                                fieldWithPath("content[].domain").description("도메인 (MOVIE/TV/GAME/WEBTOON/WEBNOVEL)"),
                                fieldWithPath("content[].tint").description("커버 틴트 (PINE/OLIVE/SLATE/TERRACOTTA/MOCHA/PLUM)"),
                                fieldWithPath("content[].visibility").description("공개 범위 (PUBLIC/PRIVATE)"),
                                fieldWithPath("content[].likeCount").description("좋아요 수"),
                                fieldWithPath("content[].viewCount").description("조회수"),
                                fieldWithPath("content[].itemCount").description("담긴 작품 수"),
                                fieldWithPath("content[].curatorNickname").description("큐레이터 닉네임"),
                                fieldWithPath("content[].coverPosters").description("커버 콜라주용 상위 3개 포스터 URL (조회 시 파생)"),
                                fieldWithPath("content[].likedByMe").description("로그인 사용자의 좋아요 여부 (비로그인 시 false)"),
                                fieldWithPath("content[].createdAt").description("생성일시"),
                                fieldWithPath("page").description("현재 페이지 번호"),
                                fieldWithPath("size").description("페이지 사이즈"),
                                fieldWithPath("totalElements").description("전체 컬렉션 수"),
                                fieldWithPath("totalPages").description("전체 페이지 수"),
                                fieldWithPath("first").description("첫 페이지 여부"),
                                fieldWithPath("last").description("마지막 페이지 여부")
                        )
                ));
    }

    @Test
    void getCollectionDetail() throws Exception {
        // given
        CollectionDetailDTO mockResponse = CollectionDetailDTO.builder()
                .id(1L)
                .title("인생 무협 웹툰 모음")
                .description("정주행 보장. 무협 입문자에게 추천하는 컬렉션입니다.")
                .domain("WEBTOON")
                .tint("PINE")
                .visibility("PUBLIC")
                .likeCount(42)
                .viewCount(1025L)
                .itemCount(1L)
                .curatorNickname("curator")
                .coverPosters(List.of("http://example.com/poster1.jpg"))
                .likedByMe(true)
                .owner(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .items(List.of(sampleItem()))
                .build();
        given(jwtTokenProvider.getUsername(anyString())).willReturn("curator");
        given(collectionService.getCollectionDetail(anyLong(), any())).willReturn(mockResponse);

        // when & then
        mockMvc.perform(get("/api/collections/{id}", 1L)
                        .header("Authorization", "Bearer mock-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("collections-get-detail",
                        pathParameters(
                                parameterWithName("id").description("컬렉션 ID")
                        ),
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰 (Bearer 타입) - 옵션. PRIVATE 컬렉션은 소유자 외 403").optional()
                        ),
                        responseFields(
                                fieldWithPath("id").description("컬렉션 고유 ID"),
                                fieldWithPath("title").description("컬렉션 제목"),
                                fieldWithPath("description").description("컬렉션 설명 (null 가능)").optional(),
                                fieldWithPath("domain").description("도메인 (MOVIE/TV/GAME/WEBTOON/WEBNOVEL)"),
                                fieldWithPath("tint").description("커버 틴트"),
                                fieldWithPath("visibility").description("공개 범위 (PUBLIC/PRIVATE)"),
                                fieldWithPath("likeCount").description("좋아요 수"),
                                fieldWithPath("viewCount").description("조회수 (이번 조회 +1 반영)"),
                                fieldWithPath("itemCount").description("담긴 작품 수"),
                                fieldWithPath("curatorNickname").description("큐레이터 닉네임"),
                                fieldWithPath("coverPosters").description("커버 콜라주용 상위 3개 포스터 URL"),
                                fieldWithPath("likedByMe").description("로그인 사용자의 좋아요 여부"),
                                fieldWithPath("owner").description("요청자가 소유자인지 여부 (편집 진입 판단용)"),
                                fieldWithPath("createdAt").description("생성일시"),
                                fieldWithPath("updatedAt").description("수정일시"),
                                fieldWithPath("items[].itemId").description("아이템 고유 ID"),
                                fieldWithPath("items[].contentId").description("작품 고유 ID"),
                                fieldWithPath("items[].comment").description("큐레이터 한 줄 코멘트 (null 가능)").optional(),
                                fieldWithPath("items[].position").description("큐레이터가 정한 정렬 순서 (0부터)"),
                                fieldWithPath("items[].title").description("작품 제목"),
                                fieldWithPath("items[].posterUrl").description("포스터 이미지 URL"),
                                fieldWithPath("items[].releaseDate").description("출시일 (yyyy-MM-dd, null 가능)").optional(),
                                fieldWithPath("items[].domain").description("작품 도메인"),
                                fieldWithPath("items[].score").description("내부 평점 (null 가능)").optional(),
                                fieldWithPath("items[].creator").description("도메인별 대표 제작자: 감독/개발사/작가 (null 가능)").optional(),
                                fieldWithPath("items[].genres").description("장르 목록 (null 가능)").optional(),
                                fieldWithPath("items[].platforms").description("플랫폼 목록 (null 가능)").optional(),
                                fieldWithPath("items[].weekday").description("웹툰 연재 요일 (웹툰 외 null)").optional(),
                                fieldWithPath("items[].status").description("웹툰 연재 상태 (웹툰 외 null)").optional(),
                                fieldWithPath("items[].ageRating").description("연령 등급 (웹툰/웹소설 외 null)").optional(),
                                fieldWithPath("items[].steamReviewDesc").description("Steam 리뷰 요약 (게임 외 null)").optional(),
                                fieldWithPath("items[].steamPositivePct").description("Steam 긍정 비율 % (게임 외 null)").optional(),
                                fieldWithPath("items[].externalRating").description("TMDB 평점 (영화/TV 외 null)").optional()
                        )
                ));
    }

    @Test
    void createCollection() throws Exception {
        // given
        CollectionCreateRequest request = new CollectionCreateRequest(
                "인생 무협 웹툰 모음", "정주행 보장. 무협 입문자에게 추천하는 컬렉션입니다.", "WEBTOON", "PINE", "PUBLIC");
        CollectionSummaryDTO mockResponse = CollectionSummaryDTO.builder()
                .id(1L)
                .title("인생 무협 웹툰 모음")
                .description("정주행 보장. 무협 입문자에게 추천하는 컬렉션입니다.")
                .domain("WEBTOON")
                .tint("PINE")
                .visibility("PUBLIC")
                .likeCount(0)
                .viewCount(0L)
                .itemCount(0L)
                .curatorNickname("curator")
                .coverPosters(List.of())
                .likedByMe(false)
                .createdAt(LocalDateTime.now())
                .build();
        given(jwtTokenProvider.getUsername(anyString())).willReturn("curator");
        given(collectionService.createCollection(anyString(), any())).willReturn(mockResponse);

        // when & then
        mockMvc.perform(post("/api/collections")
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("collections-create",
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰 (필수)")
                        ),
                        requestFields(
                                fieldWithPath("title").description("컬렉션 제목 (필수, 60자 이내)"),
                                fieldWithPath("description").description("설명 (선택, 300자 이내)").optional(),
                                fieldWithPath("domain").description("도메인 (필수, MOVIE/TV/GAME/WEBTOON/WEBNOVEL)"),
                                fieldWithPath("tint").description("커버 틴트 (선택, 기본 PINE)").optional(),
                                fieldWithPath("visibility").description("공개 범위 (선택, 기본 PUBLIC)").optional()
                        ),
                        responseFields(
                                fieldWithPath("id").description("생성된 컬렉션 고유 ID"),
                                fieldWithPath("title").description("컬렉션 제목"),
                                fieldWithPath("description").description("컬렉션 설명").optional(),
                                fieldWithPath("domain").description("도메인"),
                                fieldWithPath("tint").description("커버 틴트"),
                                fieldWithPath("visibility").description("공개 범위"),
                                fieldWithPath("likeCount").description("좋아요 수 (0)"),
                                fieldWithPath("viewCount").description("조회수 (0)"),
                                fieldWithPath("itemCount").description("담긴 작품 수 (0)"),
                                fieldWithPath("curatorNickname").description("큐레이터 닉네임"),
                                fieldWithPath("coverPosters").description("커버 포스터 (빈 배열)"),
                                fieldWithPath("likedByMe").description("좋아요 여부 (false)"),
                                fieldWithPath("createdAt").description("생성일시")
                        )
                ));
    }

    @Test
    void addItem() throws Exception {
        // given
        CollectionItemAddRequest request = new CollectionItemAddRequest(100L, "이것부터 보세요. 최고의 도입부.");
        given(jwtTokenProvider.getUsername(anyString())).willReturn("curator");
        given(collectionService.addItem(anyLong(), anyString(), any())).willReturn(sampleItem());

        // when & then
        mockMvc.perform(post("/api/collections/{id}/items", 1L)
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("collections-add-item",
                        pathParameters(
                                parameterWithName("id").description("컬렉션 ID")
                        ),
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰 (필수, 소유자만)")
                        ),
                        requestFields(
                                fieldWithPath("contentId").description("담을 작품 ID (필수). 컬렉션 도메인과 불일치 시 400, 이미 담긴 작품이면 409"),
                                fieldWithPath("comment").description("큐레이터 한 줄 코멘트 (선택, 200자 이내)").optional()
                        ),
                        responseFields(
                                fieldWithPath("itemId").description("생성된 아이템 고유 ID"),
                                fieldWithPath("contentId").description("작품 고유 ID"),
                                fieldWithPath("comment").description("큐레이터 코멘트").optional(),
                                fieldWithPath("position").description("배치된 정렬 순서 (항상 말미)"),
                                fieldWithPath("title").description("작품 제목"),
                                fieldWithPath("posterUrl").description("포스터 이미지 URL"),
                                fieldWithPath("releaseDate").description("출시일").optional(),
                                fieldWithPath("domain").description("작품 도메인"),
                                fieldWithPath("score").description("내부 평점").optional(),
                                fieldWithPath("creator").description("도메인별 대표 제작자").optional(),
                                fieldWithPath("genres").description("장르 목록").optional(),
                                fieldWithPath("platforms").description("플랫폼 목록").optional(),
                                fieldWithPath("weekday").description("웹툰 연재 요일").optional(),
                                fieldWithPath("status").description("웹툰 연재 상태").optional(),
                                fieldWithPath("ageRating").description("연령 등급").optional(),
                                fieldWithPath("steamReviewDesc").description("Steam 리뷰 요약").optional(),
                                fieldWithPath("steamPositivePct").description("Steam 긍정 비율 %").optional(),
                                fieldWithPath("externalRating").description("TMDB 평점").optional()
                        )
                ));
    }

    @Test
    void likeCollection() throws Exception {
        // given
        given(jwtTokenProvider.getUsername(anyString())).willReturn("fan");
        given(collectionService.like(anyLong(), anyString()))
                .willReturn(Map.of("collectionId", 1L, "liked", true, "likeCount", 43L));

        // when & then
        mockMvc.perform(post("/api/collections/{id}/like", 1L)
                        .header("Authorization", "Bearer mock-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("collections-like",
                        pathParameters(
                                parameterWithName("id").description("컬렉션 ID")
                        ),
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰 (필수). 멱등 - 이미 좋아요 상태면 no-op")
                        ),
                        responseFields(
                                fieldWithPath("collectionId").description("컬렉션 ID"),
                                fieldWithPath("liked").description("좋아요 상태 (항상 true)"),
                                fieldWithPath("likeCount").description("갱신된 좋아요 수")
                        )
                ));
    }

    @Test
    void getMyCollectionSummaries() throws Exception {
        // given
        MyCollectionSummaryDTO mockDto = MyCollectionSummaryDTO.builder()
                .id(1L)
                .title("인생 무협 웹툰 모음")
                .domain("WEBTOON")
                .tint("PINE")
                .visibility("PUBLIC")
                .itemCount(7L)
                .containsContent(true)
                .build();
        given(jwtTokenProvider.getUsername(anyString())).willReturn("curator");
        given(collectionService.getMyCollectionSummaries(anyString(), anyLong()))
                .willReturn(List.of(mockDto));

        // when & then
        mockMvc.perform(get("/api/collections/mine/summary")
                        .header("Authorization", "Bearer mock-token")
                        .param("contentId", "100")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("collections-mine-summary",
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰 (필수)")
                        ),
                        queryParameters(
                                parameterWithName("contentId").description("담기 대상 작품 ID (해당 작품 도메인의 내 컬렉션만 반환)")
                        ),
                        responseFields(
                                fieldWithPath("[].id").description("컬렉션 고유 ID"),
                                fieldWithPath("[].title").description("컬렉션 제목"),
                                fieldWithPath("[].domain").description("도메인"),
                                fieldWithPath("[].tint").description("커버 틴트"),
                                fieldWithPath("[].visibility").description("공개 범위"),
                                fieldWithPath("[].itemCount").description("담긴 작품 수"),
                                fieldWithPath("[].containsContent").description("조회한 작품이 이미 담겨 있는지 여부")
                        )
                ));
    }

    @Test
    void getMyCollections() throws Exception {
        // given
        PageResponse<CollectionSummaryDTO> mockResponse = PageResponse.<CollectionSummaryDTO>builder()
                .content(Collections.singletonList(sampleSummary()))
                .page(0).size(20).totalElements(1L).totalPages(1).first(true).last(true)
                .build();
        given(jwtTokenProvider.getUsername(anyString())).willReturn("curator");
        given(collectionService.getMyCollections(anyString(), anyInt(), anyInt())).willReturn(mockResponse);

        // when & then
        mockMvc.perform(get("/api/collections/mine")
                        .header("Authorization", "Bearer mock-token")
                        .param("page", "0")
                        .param("size", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("collections-mine",
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰 (필수)")
                        ),
                        queryParameters(
                                parameterWithName("page").description("페이지 번호 (0부터 시작)").optional(),
                                parameterWithName("size").description("페이지 크기").optional()
                        ),
                        responseFields(
                                fieldWithPath("content[].id").description("컬렉션 고유 ID"),
                                fieldWithPath("content[].title").description("컬렉션 제목"),
                                fieldWithPath("content[].description").description("컬렉션 설명 (null 가능)").optional(),
                                fieldWithPath("content[].domain").description("도메인"),
                                fieldWithPath("content[].tint").description("커버 틴트"),
                                fieldWithPath("content[].visibility").description("공개 범위 (PUBLIC/PRIVATE — 비공개 포함)"),
                                fieldWithPath("content[].likeCount").description("좋아요 수"),
                                fieldWithPath("content[].viewCount").description("조회수"),
                                fieldWithPath("content[].itemCount").description("담긴 작품 수"),
                                fieldWithPath("content[].curatorNickname").description("큐레이터 닉네임 (본인)"),
                                fieldWithPath("content[].coverPosters").description("커버 콜라주용 상위 3개 포스터 URL"),
                                fieldWithPath("content[].likedByMe").description("내가 좋아요한 컬렉션인지 여부"),
                                fieldWithPath("content[].createdAt").description("생성일시"),
                                fieldWithPath("page").description("현재 페이지 번호"),
                                fieldWithPath("size").description("페이지 사이즈"),
                                fieldWithPath("totalElements").description("전체 컬렉션 수"),
                                fieldWithPath("totalPages").description("전체 페이지 수"),
                                fieldWithPath("first").description("첫 페이지 여부"),
                                fieldWithPath("last").description("마지막 페이지 여부")
                        )
                ));
    }

    @Test
    void updateCollection() throws Exception {
        // given
        CollectionUpdateRequest request = new CollectionUpdateRequest(
                "인생 무협 웹툰 모음 (개정판)", "설명도 새로 고쳤습니다.", "SLATE", "PRIVATE");
        given(jwtTokenProvider.getUsername(anyString())).willReturn("curator");
        given(collectionService.updateCollection(anyLong(), anyString(), any())).willReturn(sampleSummary());

        // when & then
        mockMvc.perform(patch("/api/collections/{id}", 1L)
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("collections-update",
                        pathParameters(
                                parameterWithName("id").description("컬렉션 ID")
                        ),
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰 (필수, 소유자만)")
                        ),
                        requestFields(
                                fieldWithPath("title").description("수정할 제목 (선택, 60자 이내 — null이면 미변경)").optional(),
                                fieldWithPath("description").description("수정할 설명 (선택, 300자 이내)").optional(),
                                fieldWithPath("tint").description("수정할 커버 틴트 (선택)").optional(),
                                fieldWithPath("visibility").description("수정할 공개 범위 (선택). domain은 변경 불가").optional()
                        ),
                        responseFields(
                                fieldWithPath("id").description("컬렉션 고유 ID"),
                                fieldWithPath("title").description("컬렉션 제목"),
                                fieldWithPath("description").description("컬렉션 설명").optional(),
                                fieldWithPath("domain").description("도메인"),
                                fieldWithPath("tint").description("커버 틴트"),
                                fieldWithPath("visibility").description("공개 범위"),
                                fieldWithPath("likeCount").description("좋아요 수"),
                                fieldWithPath("viewCount").description("조회수"),
                                fieldWithPath("itemCount").description("담긴 작품 수"),
                                fieldWithPath("curatorNickname").description("큐레이터 닉네임"),
                                fieldWithPath("coverPosters").description("커버 포스터 URL 목록"),
                                fieldWithPath("likedByMe").description("좋아요 여부"),
                                fieldWithPath("createdAt").description("생성일시")
                        )
                ));
    }

    @Test
    void deleteCollection() throws Exception {
        // given
        given(jwtTokenProvider.getUsername(anyString())).willReturn("curator");

        // when & then
        mockMvc.perform(delete("/api/collections/{id}", 1L)
                        .header("Authorization", "Bearer mock-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("collections-delete",
                        pathParameters(
                                parameterWithName("id").description("삭제할 컬렉션 ID")
                        ),
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰 (필수, 소유자만)")
                        ),
                        responseFields(
                                fieldWithPath("message").description("결과 메시지")
                        )
                ));
    }

    @Test
    void updateItem() throws Exception {
        // given
        CollectionItemUpdateRequest request = new CollectionItemUpdateRequest("코멘트를 고쳤습니다.", 2);
        given(jwtTokenProvider.getUsername(anyString())).willReturn("curator");
        given(collectionService.updateItem(anyLong(), anyLong(), anyString(), any())).willReturn(sampleItem());

        // when & then
        mockMvc.perform(patch("/api/collections/{id}/items/{itemId}", 1L, 11L)
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("collections-update-item",
                        pathParameters(
                                parameterWithName("id").description("컬렉션 ID"),
                                parameterWithName("itemId").description("수정할 아이템 ID")
                        ),
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰 (필수, 소유자만)")
                        ),
                        requestFields(
                                fieldWithPath("comment").description("수정할 코멘트 (선택, 200자 이내 — 빈 문자열이면 삭제)").optional(),
                                fieldWithPath("position").description("수정할 정렬 위치 (선택, 0 이상 — 일괄 재정렬은 PUT .../items/order)").optional()
                        ),
                        responseFields(
                                fieldWithPath("itemId").description("아이템 고유 ID"),
                                fieldWithPath("contentId").description("작품 고유 ID"),
                                fieldWithPath("comment").description("큐레이터 코멘트").optional(),
                                fieldWithPath("position").description("정렬 순서"),
                                fieldWithPath("title").description("작품 제목"),
                                fieldWithPath("posterUrl").description("포스터 이미지 URL"),
                                fieldWithPath("releaseDate").description("출시일").optional(),
                                fieldWithPath("domain").description("작품 도메인"),
                                fieldWithPath("score").description("내부 평점").optional(),
                                fieldWithPath("creator").description("도메인별 대표 제작자").optional(),
                                fieldWithPath("genres").description("장르 목록").optional(),
                                fieldWithPath("platforms").description("플랫폼 목록").optional(),
                                fieldWithPath("weekday").description("웹툰 연재 요일").optional(),
                                fieldWithPath("status").description("웹툰 연재 상태").optional(),
                                fieldWithPath("ageRating").description("연령 등급").optional(),
                                fieldWithPath("steamReviewDesc").description("Steam 리뷰 요약").optional(),
                                fieldWithPath("steamPositivePct").description("Steam 긍정 비율 %").optional(),
                                fieldWithPath("externalRating").description("TMDB 평점").optional()
                        )
                ));
    }

    @Test
    void deleteItem() throws Exception {
        // given
        given(jwtTokenProvider.getUsername(anyString())).willReturn("curator");

        // when & then
        mockMvc.perform(delete("/api/collections/{id}/items/{itemId}", 1L, 11L)
                        .header("Authorization", "Bearer mock-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("collections-delete-item",
                        pathParameters(
                                parameterWithName("id").description("컬렉션 ID"),
                                parameterWithName("itemId").description("삭제할 아이템 ID")
                        ),
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰 (필수, 소유자만)")
                        ),
                        responseFields(
                                fieldWithPath("message").description("결과 메시지")
                        )
                ));
    }

    @Test
    void reorderItems() throws Exception {
        // given
        CollectionOrderRequest request = new CollectionOrderRequest(List.of(12L, 11L, 13L));
        given(jwtTokenProvider.getUsername(anyString())).willReturn("curator");

        // when & then
        mockMvc.perform(put("/api/collections/{id}/items/order", 1L)
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("collections-reorder-items",
                        pathParameters(
                                parameterWithName("id").description("컬렉션 ID")
                        ),
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰 (필수, 소유자만)")
                        ),
                        requestFields(
                                fieldWithPath("itemIds").description("원하는 순서대로 나열한 전체 아이템 ID (컬렉션 아이템과 정확히 일치해야 함, 불일치 시 400)")
                        ),
                        responseFields(
                                fieldWithPath("message").description("결과 메시지")
                        )
                ));
    }

    @Test
    void unlikeCollection() throws Exception {
        // given
        given(jwtTokenProvider.getUsername(anyString())).willReturn("fan");
        given(collectionService.unlike(anyLong(), anyString()))
                .willReturn(Map.of("collectionId", 1L, "liked", false, "likeCount", 42L));

        // when & then
        mockMvc.perform(delete("/api/collections/{id}/like", 1L)
                        .header("Authorization", "Bearer mock-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("collections-unlike",
                        pathParameters(
                                parameterWithName("id").description("컬렉션 ID")
                        ),
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰 (필수). 멱등 - 좋아요 상태가 아니면 no-op")
                        ),
                        responseFields(
                                fieldWithPath("collectionId").description("컬렉션 ID"),
                                fieldWithPath("liked").description("좋아요 상태 (항상 false)"),
                                fieldWithPath("likeCount").description("갱신된 좋아요 수")
                        )
                ));
    }
}
