package com.example.AOD.recommend.notinterested;

import com.example.AOD.recommend.auth.RecAuth;
import com.example.AOD.recommend.context.RecContextHolder;
import com.example.AOD.recommend.reaction.ContentNotFoundException;
import com.example.AOD.security.JwtTokenProvider;
import com.example.AOD.security.SecurityConfig;
import com.example.AOD.user.model.User;
import com.example.AOD.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotInterestedController.class)
@Import({SecurityConfig.class, RecAuth.class})
class NotInterestedControllerTest {

    @Autowired MockMvc mvc;
    @MockBean NotInterestedService notInterestedService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;

    @AfterEach
    void clearContext() {
        RecContextHolder.clear();   // 슬라이스에는 RecContextFilter 의 finally 가 없다
    }

    private void givenValidToken() {
        User user = new User();
        user.setId(7L);
        user.setUsername("tester");
        given(jwtTokenProvider.validateToken("good")).willReturn(true);
        given(jwtTokenProvider.getUsername("good")).willReturn("tester");
        given(userRepository.findByUsername("tester")).willReturn(Optional.of(user));
    }

    @Test
    void putTurnsItOn() throws Exception {
        givenValidToken();
        given(notInterestedService.turnOn(7L, 42L)).willReturn(true);

        mvc.perform(put("/api/recommendations/not-interested/{id}", 42L)
                        .header("Authorization", "Bearer good")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"11111111-1111-1111-1111-111111111111\","
                                + "\"impressionId\":\"22222222-2222-2222-2222-222222222222\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.on").value(true));

        verify(notInterestedService).turnOn(7L, 42L);
    }

    @Test
    void putWithoutBodyIsAllowed() throws Exception {
        givenValidToken();
        given(notInterestedService.turnOn(7L, 42L)).willReturn(true);

        mvc.perform(put("/api/recommendations/not-interested/{id}", 42L)
                        .header("Authorization", "Bearer good"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.on").value(true));
    }

    @Test
    void deleteTurnsItOff() throws Exception {
        givenValidToken();
        given(notInterestedService.turnOff(7L, 42L)).willReturn(false);

        mvc.perform(delete("/api/recommendations/not-interested/{id}", 42L)
                        .header("Authorization", "Bearer good"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.on").value(false));
    }

    @Test
    void missingOrInvalidTokenIs401() throws Exception {
        mvc.perform(put("/api/recommendations/not-interested/{id}", 42L))
                .andExpect(status().isUnauthorized());

        given(jwtTokenProvider.validateToken("expired")).willReturn(false);
        mvc.perform(delete("/api/recommendations/not-interested/{id}", 42L)
                        .header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized());

        verify(notInterestedService, never()).turnOn(anyLong(), anyLong());
        verify(notInterestedService, never()).turnOff(anyLong(), anyLong());
    }

    @Test
    void unknownContentIs404() throws Exception {
        givenValidToken();
        given(notInterestedService.turnOn(7L, 99L)).willThrow(new ContentNotFoundException(99L));

        mvc.perform(put("/api/recommendations/not-interested/{id}", 99L)
                        .header("Authorization", "Bearer good"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Content not found: 99"));
    }
}
