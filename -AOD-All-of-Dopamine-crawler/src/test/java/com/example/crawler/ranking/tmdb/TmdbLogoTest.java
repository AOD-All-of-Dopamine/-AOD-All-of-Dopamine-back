package com.example.crawler.ranking.tmdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** TMDB 히어로 로고 — 한국어 우선 · 같은 언어면 투표 평균 · PNG 만. */
class TmdbLogoTest {

    private final RestTemplate rest = mock(RestTemplate.class);
    private final TmdbRankingFetcher fetcher = new TmdbRankingFetcher(rest, new ObjectMapper());

    {
        ReflectionTestUtils.setField(fetcher, "tmdbApiKey", "k");
        ReflectionTestUtils.setField(fetcher, "tmdbBaseUrl", "https://api.themoviedb.org/3");
    }

    @Test
    void koreanFirstThenVotesPngOnly() {
        given(rest.getForObject(anyString(), eq(String.class))).willReturn("""
                {"logos":[
                  {"file_path":"/en_hi.png","iso_639_1":"en","vote_average":9.0},
                  {"file_path":"/ko.svg","iso_639_1":"ko","vote_average":9.9},
                  {"file_path":"/ko_lo.png","iso_639_1":"ko","vote_average":3.0},
                  {"file_path":"/ko_hi.png","iso_639_1":"ko","vote_average":5.0}]}
                """);
        Optional<String[]> logo = fetcher.fetchLogo(TmdbPlatformType.MOVIE, "157336");
        assertThat(logo).isPresent();
        assertThat(logo.get()[0]).isEqualTo("https://image.tmdb.org/t/p/w500/ko_hi.png");
        assertThat(logo.get()[1]).isEqualTo("ko");
    }

    @Test
    void otherLanguageAndNone() {
        given(rest.getForObject(anyString(), eq(String.class))).willReturn("{\"logos\":[{\"file_path\":\"/en.png\",\"iso_639_1\":\"en\"}]}");
        assertThat(fetcher.fetchLogo(TmdbPlatformType.TV, "2316").get()[1]).isEqualTo("other");
        given(rest.getForObject(anyString(), eq(String.class))).willReturn("{\"logos\":[{\"file_path\":\"/x.svg\",\"iso_639_1\":\"en\"}]}");
        assertThat(fetcher.fetchLogo(TmdbPlatformType.TV, "2316")).isEmpty();
    }

    @Test
    void failureThrows() {
        given(rest.getForObject(anyString(), eq(String.class))).willThrow(new ResourceAccessException("timeout"));
        assertThatThrownBy(() -> fetcher.fetchLogo(TmdbPlatformType.MOVIE, "1")).isInstanceOf(IllegalStateException.class);
    }
}
