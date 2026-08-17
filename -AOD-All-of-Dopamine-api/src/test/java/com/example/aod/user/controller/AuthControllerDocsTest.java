package com.example.AOD.user.controller;

import com.example.AOD.security.JwtTokenProvider;
import com.example.AOD.support.RestDocsTestSupport;
import com.example.AOD.user.dto.LoginRequest;
import com.example.AOD.user.dto.SignUpRequest;
import com.example.AOD.user.model.User;
import com.example.AOD.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AuthControllerDocsTest extends RestDocsTestSupport {

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void signup() throws Exception {
        // given
        SignUpRequest request = new SignUpRequest();
        request.setUsername("newUser");
        request.setEmail("newuser@example.com");
        request.setPassword("password123!");

        given(userRepository.existsByUsername(anyString())).willReturn(false);
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("encodedPassword");
        given(userRepository.save(any())).willReturn(new User());

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("auth-signup",
                        requestFields(
                                fieldWithPath("username").description("사용할 아이디 (닉네임)"),
                                fieldWithPath("email").description("이메일 주소"),
                                fieldWithPath("password").description("비밀번호"),
                                fieldWithPath("preferredGenres").description("선호 장르").optional(),
                                fieldWithPath("preferredContentTypes").description("선호 컨텐츠 타입").optional(),
                                fieldWithPath("ageGroup").description("연령대").optional(),
                                fieldWithPath("preferredAgeRating").description("선호 시청 등급").optional(),
                                fieldWithPath("favoriteDirectors").description("좋아하는 감독").optional(),
                                fieldWithPath("favoriteAuthors").description("좋아하는 작가").optional(),
                                fieldWithPath("favoriteActors").description("좋아하는 배우").optional(),
                                fieldWithPath("likesNewContent").description("신작 선호 여부").optional(),
                                fieldWithPath("likesClassicContent").description("고전작 선호 여부").optional(),
                                fieldWithPath("additionalNotes").description("추가 메모").optional()
                        ),
                        responseFields(
                                fieldWithPath("message").description("결과 메시지"),
                                fieldWithPath("username").description("가입된 아이디")
                        )
                ));
    }

    @Test
    void login() throws Exception {
        // given
        LoginRequest request = new LoginRequest();
        request.setUsername("testUser");
        request.setPassword("password123!");

        User mockUser = new User();
        mockUser.setId(10L);
        mockUser.setUsername("testUser");
        mockUser.setPassword("encodedPassword");
        mockUser.setRoles(Collections.singletonList("ROLE_USER"));

        given(userRepository.findByUsername("testUser")).willReturn(Optional.of(mockUser));
        given(passwordEncoder.matches("password123!", "encodedPassword")).willReturn(true);
        given(jwtTokenProvider.createToken("testUser", mockUser.getRoles())).willReturn("jwt.mock.token");

        // when & then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("auth-login",
                        requestFields(
                                fieldWithPath("username").description("아이디"),
                                fieldWithPath("password").description("비밀번호")
                        ),
                        responseFields(
                                fieldWithPath("token").description("발급된 JWT 인증 토큰"),
                                fieldWithPath("username").description("로그인된 아이디"),
                                fieldWithPath("userId").description("유저 고유 ID"),
                                fieldWithPath("message").description("결과 메시지")
                        )
                ));
    }

    @Test
    void checkDuplicate() throws Exception {
        // given
        Map<String, String> request = Map.of(
                "username", "testUser",
                "email", "testuser@example.com"
        );

        given(userRepository.existsByUsername("testUser")).willReturn(false);
        given(userRepository.existsByEmail("testuser@example.com")).willReturn(false);

        // when & then
        mockMvc.perform(post("/api/auth/check-duplicate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("auth-check-duplicate",
                        requestFields(
                                fieldWithPath("username").description("중복 검사할 아이디 (선택)").optional(),
                                fieldWithPath("email").description("중복 검사할 이메일 (선택)").optional()
                        ),
                        responseFields(
                                fieldWithPath("usernameExists").description("아이디 중복 여부 (true/false)"),
                                fieldWithPath("emailExists").description("이메일 중복 여부 (true/false)"),
                                fieldWithPath("message").description("결괏값 메시지")
                        )
                ));
    }

    @Test
    void getCurrentUser() throws Exception {
        // given
        User mockUser = new User();
        mockUser.setId(10L);
        mockUser.setUsername("testUser");
        mockUser.setEmail("testuser@example.com");

        given(jwtTokenProvider.getUsername("mock-token")).willReturn("testUser");
        given(userRepository.findByUsername("testUser")).willReturn(Optional.of(mockUser));

        // when & then
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer mock-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("auth-me",
                        requestHeaders(
                                headerWithName("Authorization").description("JWT 토큰")
                        ),
                        responseFields(
                                fieldWithPath("userId").description("유저 고유 ID"),
                                fieldWithPath("username").description("아이디 (닉네임)"),
                                fieldWithPath("email").description("이메일 주소")
                        )
                ));
    }
}
