package com.example.AOD.recommend.reaction;

import com.example.AOD.security.JwtTokenProvider;
import com.example.AOD.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReactionController.class)
@Import(SecurityConfig.class)
class ReactionControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ReactionService reactionService;
    @MockBean JwtTokenProvider jwtTokenProvider;

    private void givenValidToken() {
        given(jwtTokenProvider.validateToken("good")).willReturn(true);
        given(jwtTokenProvider.getUsername("good")).willReturn("tester");
    }

    @Test
    void setsStateAndReturnsPreviousState() throws Exception {
        givenValidToken();
        given(reactionService.setReaction(10L, "tester", ReactionState.DISLIKE))
                .willReturn(new ReactionResult(ReactionState.DISLIKE, ReactionState.LIKE, 10L, 2L));

        mvc.perform(put("/api/works/{id}/reaction", 10L)
                        .header("Authorization", "Bearer good")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"dislike\",\"source\":\"rec_tab\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DISLIKE"))
                .andExpect(jsonPath("$.previousState").value("LIKE"))
                .andExpect(jsonPath("$.likeCount").value(10))
                .andExpect(jsonPath("$.dislikeCount").value(2));
    }

    @Test
    void missingOrInvalidTokenIs401() throws Exception {
        mvc.perform(put("/api/works/{id}/reaction", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"LIKE\"}"))
                .andExpect(status().isUnauthorized());

        given(jwtTokenProvider.validateToken("expired")).willReturn(false);
        mvc.perform(put("/api/works/{id}/reaction", 10L)
                        .header("Authorization", "Bearer expired")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"LIKE\"}"))
                .andExpect(status().isUnauthorized());

        verify(reactionService, never()).setReaction(anyLong(), anyString(), eq(ReactionState.LIKE));
    }

    @Test
    void unknownStateIs400() throws Exception {
        givenValidToken();

        mvc.perform(put("/api/works/{id}/reaction", 10L)
                        .header("Authorization", "Bearer good")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"LOVE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingContentIs404() throws Exception {
        givenValidToken();
        given(reactionService.setReaction(99L, "tester", ReactionState.LIKE))
                .willThrow(new ContentNotFoundException(99L));

        mvc.perform(put("/api/works/{id}/reaction", 99L)
                        .header("Authorization", "Bearer good")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"LIKE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Content not found: 99"));
    }
}
