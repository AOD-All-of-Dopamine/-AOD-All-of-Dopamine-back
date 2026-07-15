package com.example.crawler.ingest;

import com.example.crawler.ingest.rule.PlatformRule;
import com.example.shared.entity.*;
import com.example.shared.repository.*;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DraftAssemblerTest {

    private final DomainCatalog catalog = new DomainCatalog(
            mock(MovieContentRepository.class), mock(TvContentRepository.class),
            mock(GameContentRepository.class), mock(WebtoonContentRepository.class),
            mock(WebnovelContentRepository.class));
    private final DraftAssembler assembler = new DraftAssembler(catalog);

    private PlatformRule rule(String yml) {
        return PlatformRule.parse("test", new Yaml().load(yml));
    }

    private static final String NAVER_SERIES_V4 = """
            platformName: NaverSeries
            domain: WEBNOVEL
            mappings:
              title: master.masterTitle
              imageUrl: master.posterImageUrl
              synopsis: master.synopsis
              firstDate: master.releaseDate
              author: domain.author
              publisher: domain.publisher
              ageRating: domain.ageRating
              genres: master.genres
              productUrl: platform.url
              titleId: platform.platformSpecificId
              status: attr.status
              rating: attr.rating
              downloadCount: attr.download_count
            defaults:
              attr.download_count: 0
            normalizers:
              master.masterTitle: [strip_parentheses, collapse_spaces, nfkc]
            """;

    @Test
    void goldenNaverSeries() {
        Map<String, Object> payload = Map.ofEntries(          // 12쌍 — Map.of는 10쌍 한계
                Map.entry("title", "전지적 독자 시점 (외전)"),
                Map.entry("imageUrl", "http://img"), Map.entry("synopsis", "줄거리"),
                Map.entry("firstDate", "2018-05-14"),
                Map.entry("author", "싱숑"), Map.entry("publisher", "문피아"), Map.entry("ageRating", "전체이용가"),
                Map.entry("genres", List.of("판타지")),
                Map.entry("productUrl", "http://series"), Map.entry("titleId", "3293134"),
                Map.entry("status", "연재중"), Map.entry("rating", 9.9));

        DraftAssembler.IngestDraft d = assembler.assemble(payload, rule(NAVER_SERIES_V4));

        Content c = d.content();
        assertEquals(Domain.WEBNOVEL, c.getDomain());
        assertEquals("전지적 독자 시점", c.getMasterTitle());          // 괄호제거+공백정리 (골든)
        assertEquals(LocalDate.of(2018, 5, 14), c.getReleaseDate());   // 바인딩 시점에 typed 변환
        assertEquals("줄거리", c.getSynopsis());
        assertEquals(List.of("판타지"), c.getGenres());                // 2026-07 마스터로 승격

        WebnovelContent w = (WebnovelContent) d.domainEntity();
        assertEquals("싱숑", w.getAuthor());
        assertEquals("전체이용가", w.getAgeRating());
        assertEquals(List.of("NaverSeries"), w.getPlatforms());        // 엔진 주입 (RF-4)

        PlatformData pd = d.platformData();
        assertEquals("NaverSeries", pd.getPlatformName());
        assertEquals("3293134", pd.getPlatformSpecificId());
        assertEquals("연재중", pd.getAttributes().get("status"));
        assertEquals(9.9, pd.getAttributes().get("rating"));
        assertEquals(0, pd.getAttributes().get("download_count"));     // default 채움 (RF-3)

        // yml mappings 선언 순서 + platforms 엔진 주입 (정확한 순서 핀) — genres는 마스터로 가서 제외
        assertEquals(List.of("author", "publisher", "ageRating", "platforms"), d.boundDomainProps());
    }

    private static final String TMDB_MOVIE_V4 = """
            platformName: TMDB_MOVIE
            domain: MOVIE
            mappings:
              title: master.masterTitle
              original_title: master.originalTitle
              overview: master.synopsis
              release_date: master.releaseDate
              movie_details.id: platform.platformSpecificId
              genres: master.genres
              runtime: domain.runtime
              directors: domain.directors
              cast: domain.cast
              watch_providers: attr.watch_providers
            normalizers:
              master.masterTitle: [collapse_spaces, nfkc]
              master.originalTitle: [collapse_spaces, nfkc]
            platformsFrom: [watch_providers]
            """;

    @Test
    void goldenTmdbMovieWithPlatformsFromMerge() {
        Map<String, Object> payload = Map.of(
                "title", "듄:  파트2", "original_title", "Dune: Part Two",
                "overview", "사막 행성", "release_date", "2024-02-28",
                "movie_details", Map.of("id", 693134),
                "genres", List.of("SF"), "runtime", 166,
                "directors", List.of("드니 빌뇌브"), "cast", List.of("티모시 샬라메"),
                "watch_providers", List.of("Netflix", "Disney Plus"));

        DraftAssembler.IngestDraft d = assembler.assemble(payload, rule(TMDB_MOVIE_V4));

        assertEquals("듄: 파트2", d.content().getMasterTitle());
        assertEquals(List.of("SF"), d.content().getGenres());
        MovieContent m = (MovieContent) d.domainEntity();
        assertEquals(166, m.getRuntime());
        assertEquals(List.of("티모시 샬라메"), m.getCast());
        // platformsFrom: 자기 플랫폼명 + watch_providers 병합, attributes에도 유지 (구 RF-4 골든)
        assertEquals(List.of("TMDB_MOVIE", "Netflix", "Disney Plus"), m.getPlatforms());
        assertEquals(List.of("Netflix", "Disney Plus"), d.platformData().getAttributes().get("watch_providers"));
        assertEquals("693134", d.platformData().getPlatformSpecificId()); // 중첩경로 + String 변환
    }

    @Test
    void domainDefaultIsBoundAndRecordedInBoundProps() {
        PlatformRule r = rule("""
                platformName: X
                domain: WEBNOVEL
                mappings:
                  title: master.masterTitle
                  ageRating: domain.ageRating
                defaults:
                  domain.ageRating: 전체이용가
                """);
        DraftAssembler.IngestDraft d = assembler.assemble(Map.of("title", "t"), r);
        assertEquals("전체이용가", ((WebnovelContent) d.domainEntity()).getAgeRating());
        assertTrue(d.boundDomainProps().contains("ageRating"));
    }

    @Test
    void missingSourceWithoutDefaultIsSkippedEntirely() {
        DraftAssembler.IngestDraft d = assembler.assemble(Map.of(), rule(NAVER_SERIES_V4));
        assertNull(d.content().getMasterTitle());
        assertNull(((WebnovelContent) d.domainEntity()).getAuthor());
        assertFalse(d.platformData().getAttributes().containsKey("status"));
        assertEquals(0, d.platformData().getAttributes().get("download_count")); // default만 채워짐
        assertEquals(List.of("NaverSeries"), ((WebnovelContent) d.domainEntity()).getPlatforms()); // platforms는 항상 주입
    }
}
