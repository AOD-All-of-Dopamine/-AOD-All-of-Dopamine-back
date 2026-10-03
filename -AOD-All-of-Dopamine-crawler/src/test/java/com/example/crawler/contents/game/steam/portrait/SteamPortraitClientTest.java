package com.example.crawler.contents.game.steam.portrait;

import com.example.crawler.contents.game.steam.SteamRateLimiter;
import com.example.crawler.contents.game.steam.portrait.SteamPortraitClient.Portrait;
import com.example.crawler.contents.game.steam.portrait.SteamPortraitClient.SteamPortraitException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Steam GetItems 표지 조회 — 주소 만들기 · 응답 해석 표 (설계 "SteamPortraitClient"). */
class SteamPortraitClientTest {

    private final RestTemplate rest = mock(RestTemplate.class);
    private final SteamRateLimiter limiter = mock(SteamRateLimiter.class);
    private final SteamPortraitClient client = new SteamPortraitClient(rest, new ObjectMapper(), limiter);

    private static final String BODY = """
            {"response":{"store_items":[
              {"item_type":0,"id":3513350,"success":1,"visible":true,"name":"Wuthering Waves","appid":3513350,
               "assets":{"asset_url_format":"steam/apps/3513350/${FILENAME}?t=1790713386",
                         "library_capsule":"7ba4943/library_capsule_koreana.jpg",
                         "library_capsule_2x":"7ba4943/library_capsule_koreana_2x.jpg",
                         "header":"7ba4943/header.jpg"}},
              {"item_type":0,"id":870,"success":1,"visible":true,"appid":870,
               "assets":{"asset_url_format":"steam/apps/870/${FILENAME}","header":"header.jpg"}},
              {"item_type":0,"id":1,"success":15,"visible":false,"appid":0}
            ]}}
            """;

    @Test
    void uriEncodesJsonAsOneQueryValue() {
        URI uri = client.uri(List.of(3513350L, 870L));

        assertThat(uri.toString()).startsWith(SteamPortraitClient.ENDPOINT + "?input_json=");
        assertThat(uri.getRawQuery()).doesNotContain("{").doesNotContain("\"");
        String json = URLDecoder.decode(uri.getRawQuery().substring("input_json=".length()), StandardCharsets.UTF_8);
        assertThat(json).isEqualTo("{\"ids\":[{\"appid\":3513350},{\"appid\":870}],"
                + "\"context\":{\"language\":\"koreana\",\"country_code\":\"KR\"},"
                + "\"data_request\":{\"include_assets\":true}}");
    }

    @Test
    void parseFollowsTheTable() {
        when(rest.getForObject(any(URI.class), eq(String.class))).thenReturn(BODY);

        Map<Long, Portrait> out = client.fetch(List.of(3513350L, 870L, 1L, 42L));

        // success:1 + library_capsule → 1x 주소(한국어판 그대로)
        assertThat(out.get(3513350L).portraitUrl()).isEqualTo(
                "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/3513350/7ba4943/library_capsule_koreana.jpg?t=1790713386");
        // success:1, 표지 필드 없음 → 확인함 · 표지 없음
        assertThat(out).containsKey(870L);
        assertThat(out.get(870L).portraitUrl()).isNull();
        // success ≠ 1 (없는 앱, appid 0) → id 로 짝 · 확인함 · 표지 없음
        assertThat(out.get(1L).portraitUrl()).isNull();
        // 응답에 없음 → 결과에 없음(확인 안 함)
        assertThat(out).doesNotContainKey(42L);
        verify(limiter).acquirePermit();
    }

    @Test
    void callFailureThrows() {
        when(rest.getForObject(any(URI.class), eq(String.class))).thenThrow(new ResourceAccessException("Read timed out"));
        assertThatThrownBy(() -> client.fetch(List.of(1L))).isInstanceOf(SteamPortraitException.class);
    }

    @Test
    void brokenJsonThrows() {
        when(rest.getForObject(any(URI.class), eq(String.class))).thenReturn("<html>oops</html>");
        assertThatThrownBy(() -> client.fetch(List.of(1L))).isInstanceOf(SteamPortraitException.class);
        when(rest.getForObject(any(URI.class), eq(String.class))).thenReturn("{\"response\":{}}");
        assertThatThrownBy(() -> client.fetch(List.of(1L))).isInstanceOf(SteamPortraitException.class);
    }

    @Test
    void batchLimit() {
        assertThat(client.fetch(List.of())).isEmpty();
        List<Long> tooMany = java.util.stream.LongStream.rangeClosed(1, 201).boxed().toList();
        assertThatThrownBy(() -> client.fetch(tooMany)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fetchAssetsReadsLibraryHero() {
        when(rest.getForObject(any(URI.class), eq(String.class))).thenReturn("""
                {"response":{"store_items":[
                  {"id":1091500,"success":1,"assets":{"asset_url_format":"steam/apps/1091500/${FILENAME}?t=1",
                    "library_hero":"abc/library_hero.jpg","library_hero_2x":"abc/library_hero_2x.jpg"}},
                  {"id":870,"success":1,"assets":{"asset_url_format":"steam/apps/870/${FILENAME}"}},
                  {"id":1,"success":15,"appid":0}]}}
                """);
        Map<Long, SteamPortraitClient.Assets> out = client.fetchAssets(List.of(1091500L, 870L, 1L, 2L));
        assertThat(out.get(1091500L).heroUrl())
                .isEqualTo("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1091500/abc/library_hero.jpg?t=1");
        assertThat(out.get(870L).heroUrl()).isNull();
        assertThat(out.get(1L).heroUrl()).isNull();
        assertThat(out).doesNotContainKey(2L);
    }
}
