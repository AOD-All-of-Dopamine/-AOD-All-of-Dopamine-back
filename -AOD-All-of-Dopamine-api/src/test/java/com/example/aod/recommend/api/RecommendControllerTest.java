package com.example.AOD.recommend.api;

import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.recommend.api.dto.RecommendItem;
import com.example.AOD.recommend.api.dto.RecommendResponse;
import com.example.AOD.recommend.auth.RecAuth;
import com.example.AOD.recommend.chain.ChainNotFoundException;
import com.example.AOD.recommend.context.RecContext;
import com.example.AOD.recommend.context.RecContextHolder;
import com.example.AOD.recommend.reason.RecReason;
import com.example.AOD.security.JwtTokenProvider;
import com.example.AOD.security.SecurityConfig;
import com.example.AOD.user.model.User;
import com.example.AOD.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RecommendController.class)
@Import({SecurityConfig.class, RecAuth.class})
class RecommendControllerTest {

    @Autowired MockMvc mvc;
    @MockBean RecommendService recommendService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;

    private static final UUID CHAIN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @AfterEach
    void clearContext() {
        RecContextHolder.clear();
    }

    private void givenValidToken() {
        User user = new User();
        user.setId(7L);
        user.setUsername("tester");
        given(jwtTokenProvider.validateToken("good")).willReturn(true);
        given(jwtTokenProvider.getUsername("good")).willReturn("tester");
        given(userRepository.findByUsername("tester")).willReturn(Optional.of(user));
    }

    private static RecommendResponse personalized() {
        WorkSummaryDTO work = WorkSummaryDTO.builder().id(11L).domain("GAME").title("작품11").build();
        return new RecommendResponse("22222222-2222-2222-2222-222222222222", CHAIN_ID.toString(), 1,
                false, null,
                List.of(new RecommendItem("33333333-3333-3333-3333-333333333333", 0, work,
                        new RecReason("like", 1L, "코코를 좋아해서"))),
                true);
    }

    private static RecommendResponse anonymous() {
        WorkSummaryDTO work = WorkSummaryDTO.builder().id(500L).domain("GAME").title("대체작").build();
        return new RecommendResponse("44444444-4444-4444-4444-444444444444",
                "55555555-5555-5555-5555-555555555555", 0, true, "anonymous",
                List.of(new RecommendItem("66666666-6666-6666-6666-666666666666", 0, work, null)), false);
    }

    @Test
    void returnsTheFullResponseShape() throws Exception {
        givenValidToken();
        given(recommendService.recommend(eq("all"), eq(CHAIN_ID), eq(20), eq(7L), eq("tester"), any(RecContext.class)))
                .willReturn(personalized());

        mvc.perform(get("/api/recommendations")
                        .param("tab", "all").param("chainId", CHAIN_ID.toString()).param("size", "20")
                        .header("Authorization", "Bearer good"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("22222222-2222-2222-2222-222222222222"))
                .andExpect(jsonPath("$.chainId").value(CHAIN_ID.toString()))
                .andExpect(jsonPath("$.pageDepth").value(1))
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.fallbackReason").doesNotExist())
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.items[0].impressionId").value("33333333-3333-3333-3333-333333333333"))
                .andExpect(jsonPath("$.items[0].rank").value(0))
                .andExpect(jsonPath("$.items[0].work.id").value(11))
                .andExpect(jsonPath("$.items[0].work.title").value("작품11"))
                .andExpect(jsonPath("$.items[0].reason.type").value("like"))
                .andExpect(jsonPath("$.items[0].reason.seedContentId").value(1))
                .andExpect(jsonPath("$.items[0].reason.text").value("코코를 좋아해서"));
    }

    @Test
    void defaultsAreAllAndTwenty() throws Exception {
        givenValidToken();
        given(recommendService.recommend(eq("all"), isNull(), eq(20), eq(7L), eq("tester"), any(RecContext.class)))
                .willReturn(personalized());

        mvc.perform(get("/api/recommendations").header("Authorization", "Bearer good"))
                .andExpect(status().isOk());

        verify(recommendService).recommend(eq("all"), isNull(), eq(20), eq(7L), eq("tester"), any(RecContext.class));
    }

    @Test
    void noTokenGetsAnonymousFallbackNotFourOhOne() throws Exception {
        given(recommendService.anonymousFallback(eq("game"), eq(20), any(RecContext.class)))
                .willReturn(anonymous());

        mvc.perform(get("/api/recommendations").param("tab", "game"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.fallbackReason").value("anonymous"))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.items[0].reason").doesNotExist());

        verify(recommendService, never()).recommend(anyString(), any(), anyInt(), anyLong(), anyString(), any());
    }

    @Test
    void surfaceParamReachesTheServiceAsContextSource() throws Exception {
        given(recommendService.anonymousFallback(eq("all"), eq(12), any(RecContext.class)))
                .willReturn(anonymous());

        mvc.perform(get("/api/recommendations").param("size", "12").param("surface", " Home_Rec "))
                .andExpect(status().isOk());

        ArgumentCaptor<RecContext> ctx = ArgumentCaptor.forClass(RecContext.class);
        verify(recommendService).anonymousFallback(eq("all"), eq(12), ctx.capture());
        assertEquals("home_rec", ctx.getValue().source(), "앞뒤 공백·대소문자를 정리해 넘긴다");
    }

    @Test
    void noSurfaceParamLeavesTheContextSourceEmpty() throws Exception {
        given(recommendService.anonymousFallback(eq("all"), eq(20), any(RecContext.class)))
                .willReturn(anonymous());

        mvc.perform(get("/api/recommendations")).andExpect(status().isOk());

        ArgumentCaptor<RecContext> ctx = ArgumentCaptor.forClass(RecContext.class);
        verify(recommendService).anonymousFallback(eq("all"), eq(20), ctx.capture());
        assertNull(ctx.getValue().source(), "파라미터가 없으면 서비스가 추천 탭으로 적는다");
    }

    @Test
    void userLookupFailureGivesTheFallbackNotFourOhOneOrFiveHundred() throws Exception {
        // RecAuth 의 username→id 캐시는 테스트 컨텍스트를 함께 쓰므로 이 테스트만의 사용자를 쓴다.
        given(jwtTokenProvider.validateToken("dbdown")).willReturn(true);
        given(jwtTokenProvider.getUsername("dbdown")).willReturn("db-user");
        given(userRepository.findByUsername("db-user"))
                .willThrow(new DataAccessResourceFailureException("db down"));
        WorkSummaryDTO work = WorkSummaryDTO.builder().id(500L).domain("GAME").title("대체작").build();
        given(recommendService.unavailableFallback(eq("all"), eq(20), any(RecContext.class)))
                .willReturn(new RecommendResponse("77777777-7777-7777-7777-777777777777",
                        "88888888-8888-8888-8888-888888888888", 0, true, "service_error",
                        List.of(new RecommendItem("99999999-9999-9999-9999-999999999999", 0, work, null)), false));

        mvc.perform(get("/api/recommendations").header("Authorization", "Bearer dbdown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.fallbackReason").value("service_error"));

        verify(recommendService, never()).recommend(anyString(), any(), anyInt(), anyLong(), anyString(), any());
    }

    @Test
    void invalidTokenIs401() throws Exception {
        given(jwtTokenProvider.validateToken("expired")).willReturn(false);

        mvc.perform(get("/api/recommendations").header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());

        verify(recommendService, never()).anonymousFallback(anyString(), anyInt(), any());
    }

    @Test
    void badTabSizeOrChainIdIs400() throws Exception {
        givenValidToken();

        mvc.perform(get("/api/recommendations").param("tab", "books").header("Authorization", "Bearer good"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/recommendations").param("size", "0").header("Authorization", "Bearer good"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/recommendations").param("size", "31").header("Authorization", "Bearer good"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/recommendations").param("size", "twenty").header("Authorization", "Bearer good"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/recommendations").param("chainId", "not-a-uuid").header("Authorization", "Bearer good"))
                .andExpect(status().isBadRequest());

        verify(recommendService, never()).recommend(anyString(), any(), anyInt(), anyLong(), anyString(), any());
    }

    @Test
    void tabIsCaseInsensitiveAndTrimmed() throws Exception {
        givenValidToken();
        given(recommendService.recommend(eq("webnovel"), isNull(), eq(20), eq(7L), eq("tester"), any(RecContext.class)))
                .willReturn(personalized());

        mvc.perform(get("/api/recommendations").param("tab", " WebNovel ").header("Authorization", "Bearer good"))
                .andExpect(status().isOk());
    }

    @Test
    void homeSetSizeThirtyIsAccepted() throws Exception {
        givenValidToken();
        given(recommendService.recommend(eq("all"), isNull(), eq(30), eq(7L), eq("tester"), any(RecContext.class)))
                .willReturn(personalized());

        mvc.perform(get("/api/recommendations").param("size", "30").header("Authorization", "Bearer good"))
                .andExpect(status().isOk());
    }

    @Test
    void blankChainIdIsTreatedAsAbsent() throws Exception {
        givenValidToken();
        given(recommendService.recommend(eq("all"), isNull(), eq(20), eq(7L), eq("tester"), any(RecContext.class)))
                .willReturn(personalized());

        mvc.perform(get("/api/recommendations").param("chainId", "").header("Authorization", "Bearer good"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownChainIs404() throws Exception {
        givenValidToken();
        given(recommendService.recommend(eq("all"), eq(CHAIN_ID), eq(20), eq(7L), eq("tester"), any(RecContext.class)))
                .willThrow(new ChainNotFoundException(CHAIN_ID));

        mvc.perform(get("/api/recommendations")
                        .param("chainId", CHAIN_ID.toString())
                        .header("Authorization", "Bearer good"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Chain not found: " + CHAIN_ID));
    }
}
