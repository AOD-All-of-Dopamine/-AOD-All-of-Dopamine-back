package com.example.AOD.recommend.catalog;

import com.example.AOD.security.JwtTokenProvider;
import com.example.AOD.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CatalogKeyController.class)
@Import(SecurityConfig.class)
class CatalogKeyControllerTest {

    @Autowired MockMvc mvc;
    @MockBean CatalogKeyService catalogKeyService;
    // JwtAuthenticationFilter 는 @Component(Filter) 라 SecurityConfig 를 Import 하는 모든 @WebMvcTest
    // 슬라이스에 자동으로 딸려 들어온다(ReactionControllerTest·NotInterestedControllerTest 와 같은 이유) —
    // 이 컨트롤러 자체는 인증을 쓰지 않지만 JwtTokenProvider 가 없으면 그 필터 빈 생성이 실패해 컨텍스트가 안 뜬다.
    @MockBean JwtTokenProvider jwtTokenProvider;

    @Test
    void servesPlainTextOneKeyPerLineWithoutAuthentication() throws Exception {
        given(catalogKeyService.keysText("steam")).willReturn("240\n730\n");

        mvc.perform(get("/api/recommendations/catalog-keys").param("platform", "steam"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string("240\n730\n"));
    }

    @Test
    void platformIsNormalised() throws Exception {
        given(catalogKeyService.keysText("tmdb")).willReturn("movie_603\n");

        mvc.perform(get("/api/recommendations/catalog-keys").param("platform", " TMDB "))
                .andExpect(status().isOk())
                .andExpect(content().string("movie_603\n"));

        verify(catalogKeyService).keysText("tmdb");
    }

    @Test
    void unknownPlatformIs400() throws Exception {
        given(catalogKeyService.keysText(anyString())).willThrow(new IllegalArgumentException("unknown platform"));

        mvc.perform(get("/api/recommendations/catalog-keys").param("platform", "kakao"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingPlatformIs400() throws Exception {
        mvc.perform(get("/api/recommendations/catalog-keys"))
                .andExpect(status().isBadRequest());
    }
}
