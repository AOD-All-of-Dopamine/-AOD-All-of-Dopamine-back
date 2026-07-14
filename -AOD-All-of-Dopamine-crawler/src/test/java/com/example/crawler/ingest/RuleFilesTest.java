package com.example.crawler.ingest;

import com.example.crawler.ingest.rule.RuleRegistry;
import com.example.shared.entity.*;
import com.example.shared.repository.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 프로덕션 rules/**.yml 7개가 v4로서 로드·검증 통과하고 골든 값을 내는지. */
class RuleFilesTest {

    private final DomainCatalog catalog = new DomainCatalog(
            mock(MovieContentRepository.class), mock(TvContentRepository.class),
            mock(GameContentRepository.class), mock(WebtoonContentRepository.class),
            mock(WebnovelContentRepository.class));
    private final RuleRegistry registry = new RuleRegistry("classpath*:rules/**/*.yml", catalog);
    private final DraftAssembler assembler = new DraftAssembler(catalog);

    @Test
    void allSevenProductionRulesLoad() {
        for (String p : List.of("NaverSeries", "KakaoPage", "NaverWebtoon", "Steam", "Epic", "TMDB_MOVIE", "TMDB_TV"))
            assertNotNull(registry.pathOf(p), p + " 룰이 로드되어야 함");
    }

    @Test
    void goldenSteam() {
        var d = assembler.assemble(Map.of(
                "name", "Half-Life (Anthology)", "steam_appid", 70,
                "developers", List.of("Valve"), "publishers", List.of("Valve"),
                "platforms", Map.of("windows", true, "mac", false),
                "genres", List.of("FPS"),
                "recommendations", Map.of("total", 12345)), registry.resolve("GAME", "Steam"));
        assertEquals("Half-Life", d.content().getMasterTitle());
        GameContent g = (GameContent) d.domainEntity();
        assertEquals("Valve", g.getDeveloper());                       // developers[0]
        assertEquals(Map.of("windows", true, "mac", false), g.getOsPlatforms());
        assertEquals("70", d.platformData().getPlatformSpecificId());
        assertEquals(12345, d.platformData().getAttributes().get("recommendation_count"));
        assertEquals(List.of("Steam"), g.getPlatforms());
    }

    @Test
    void goldenNaverWebtoon() {
        var d = assembler.assemble(Map.of(
                "title", "[단행본] 화산귀환 시즌2", "author", "비가",
                "status", "연재중", "weekday", "mon", "ageRating", "15세이용가",
                "genres", List.of("무협"), "titleId", "769209"), registry.resolve("WEBTOON", "NaverWebtoon"));
        assertEquals("화산귀환", d.content().getMasterTitle());          // 대괄호+시리즈수식어 제거
        WebtoonContent w = (WebtoonContent) d.domainEntity();
        assertEquals("비가", w.getAuthor());
        assertEquals("연재중", w.getStatus());
        assertEquals(List.of("무협"), w.getGenres());
        assertEquals(0, d.platformData().getAttributes().get("like_count")); // default
    }

    @Test
    void naverWebtoonLegacyTagsKeyStillMapsToGenres() {
        // 전환기 호환: genres 개명 이전에 쌓인 raw payload(tags 키)도 장르를 잃지 않아야 함
        var d = assembler.assemble(Map.of(
                "title", "참교육", "tags", List.of("액션"), "titleId", "758037"),
                registry.resolve("WEBTOON", "NaverWebtoon"));
        assertEquals(List.of("액션"), ((WebtoonContent) d.domainEntity()).getGenres());
    }

    @Test
    void goldenKakaoPage() {
        var d = assembler.assemble(Map.of(
                "title", "나 혼자만 레벨업", "author", "추공",
                "seriesId", "48734983", "viewCount", 1000), registry.resolve("WEBNOVEL", "KakaoPage"));
        assertEquals("나 혼자만 레벨업", d.content().getMasterTitle());
        assertEquals("추공", ((WebnovelContent) d.domainEntity()).getAuthor());
        assertEquals(1000, d.platformData().getAttributes().get("view_count"));
        assertEquals(0, d.platformData().getAttributes().get("comment_count")); // default
    }

    @Test
    void goldenTmdbTv() {
        var d = assembler.assemble(Map.of(
                "name", "오징어 게임", "original_name", "Squid Game",
                "first_air_date", "2021-09-17",
                "number_of_seasons", 2, "episode_run_time", List.of(54, 62),
                "tv_details", Map.of("id", 93405),
                "watch_providers", List.of("Netflix")), registry.resolve("TV", "TMDB_TV"));
        TvContent t = (TvContent) d.domainEntity();
        assertEquals(2, t.getSeasonCount());
        assertEquals(54, t.getEpisodeRuntime());                       // 배열 첫 값
        assertEquals(List.of("TMDB_TV", "Netflix"), t.getPlatforms()); // platformsFrom 병합
        assertEquals("93405", d.platformData().getPlatformSpecificId());
    }

    @Test
    void goldenEpicDormant() {
        var d = assembler.assemble(Map.of(
                "data", Map.of("title", "Alan Wake 2", "developer", "Remedy",
                        "keyImages", List.of(Map.of("url", "http://img")))), registry.resolve("GAME", "Epic"));
        assertEquals("Alan Wake 2", d.content().getMasterTitle());
        assertEquals("Remedy", ((GameContent) d.domainEntity()).getDeveloper());
        assertEquals("http://img", d.content().getPosterImageUrl());   // keyImages[0].url
    }
}
