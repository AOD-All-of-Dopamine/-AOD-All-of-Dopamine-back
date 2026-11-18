package com.example.AOD.user.controller;

import com.example.AOD.security.JwtTokenProvider;
import com.example.AOD.security.SecurityConfig;
import com.example.AOD.user.model.User;
import com.example.AOD.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 가입 응답 계약 (추천 탭 6번 — 프론트가 needsOnboarding 을 보고 온보딩으로 보낸다).
 *
 * RestDocs 테스트(AuthControllerDocsTest)는 RestDocsTestSupport 를 상속해 @SpringBootTest 라
 * 개발자 로컬 DB 에 붙는다 — 빠른 회귀는 이 슬라이스가 맡는다.
 * SecurityConfig 를 Import 해야 /api/auth/** permitAll 이 적용된다(ReactionControllerTest 관례).
 * PasswordEncoder 는 SecurityConfig 가 주는 진짜 BCrypt 빈을 그대로 쓴다 — 모의할 이유가 없다.
 * 한글 본문 값은 단언하지 않는다(MockMvc 응답 인코딩에 의존하지 않으려고) — 존재만 확인한다.
 */
@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    private static final String SIGNUP_BODY =
            "{\"username\":\"newUser\",\"email\":\"newuser@example.com\",\"password\":\"password123!\"}";

    @Autowired MockMvc mvc;
    @MockBean UserRepository userRepository;
    @MockBean JwtTokenProvider jwtTokenProvider;

    @Test
    void signupTellsClientToRunOnboarding() throws Exception {
        given(userRepository.existsByUsername(anyString())).willReturn(false);
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("newUser"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.needsOnboarding").value(true));
    }

    @Test
    void signupStoresAgreedPolicyVersion() throws Exception {
        given(userRepository.existsByUsername(anyString())).willReturn(false);
        given(userRepository.existsByEmail(anyString())).willReturn(false);

        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY.replace("}", ",\"policyVersion\":\"2026-11-18\"}")))
                .andExpect(status().isOk());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("2026-11-18", saved.getValue().getPolicyVersion());
        assertNotNull(saved.getValue().getPolicyAgreedAt());
    }

    /** 동의 기능 이전 클라이언트 — 가입은 되고 동의 기록만 비어 있다 (설계 §3 두 단계 배포). */
    @Test
    void signupWithoutPolicyVersionStillWorks() throws Exception {
        given(userRepository.existsByUsername(anyString())).willReturn(false);
        given(userRepository.existsByEmail(anyString())).willReturn(false);

        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(SIGNUP_BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertNull(saved.getValue().getPolicyVersion());
        assertNull(saved.getValue().getPolicyAgreedAt());
    }

    /** users.policy_version 은 20자다 — 넘는 값은 저장하면 가입 자체가 실패하므로 동의 기록 없이 가입시킨다. */
    @Test
    void signupIgnoresPolicyVersionLongerThanColumn() throws Exception {
        given(userRepository.existsByUsername(anyString())).willReturn(false);
        given(userRepository.existsByEmail(anyString())).willReturn(false);

        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY.replace("}", ",\"policyVersion\":\"2026-11-18-and-then-some\"}")))
                .andExpect(status().isOk());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertNull(saved.getValue().getPolicyVersion());
    }

    @Test
    void duplicateUsernameIs400AndSavesNothing() throws Exception {
        given(userRepository.existsByUsername("newUser")).willReturn(true);

        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.needsOnboarding").doesNotExist());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void duplicateEmailIs400AndSavesNothing() throws Exception {
        given(userRepository.existsByUsername(anyString())).willReturn(false);
        given(userRepository.existsByEmail("newuser@example.com")).willReturn(true);

        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.needsOnboarding").doesNotExist());

        verify(userRepository, never()).save(any(User.class));
    }
}
