package com.example.AOD.recommend.key;

import java.util.List;
import java.util.Map;

/**
 * platform_data ↔ 라우터 키 규칙 (설계 §3). 순수 함수만 — DB 도 스프링도 모른다.
 *
 * aod_rec.corpus_map 을 쓰지 않는 이유: 코퍼스 키가 전부 플랫폼 고유 ID 라
 * platform_data.platform_specific_id 가 같은 값을 이미 들고 있다. 적재 작업이 없어
 * 크롤러가 작품을 추가하면 즉시 반영된다.
 */
public final class CorpusKeyMapper {

    /** 라우터가 아는 platform 이름. */
    public static final String STEAM = "steam";
    public static final String TMDB = "tmdb";
    public static final String WEBTOON = "webtoon";
    public static final String WEBNOVEL = "webnovel";

    /** platform_data.platformName 값. */
    public static final String PD_STEAM = "Steam";
    public static final String PD_TMDB_MOVIE = "TMDB_MOVIE";
    public static final String PD_TMDB_TV = "TMDB_TV";
    public static final String PD_NAVER_WEBTOON = "NaverWebtoon";
    public static final String PD_NAVER_SERIES = "NaverSeries";

    /** 매핑이 있는 platformName 전부. 조회 WHERE 절에 그대로 넣는다. */
    public static final List<String> SUPPORTED_PLATFORM_NAMES =
            List.of(PD_STEAM, PD_TMDB_MOVIE, PD_TMDB_TV, PD_NAVER_WEBTOON, PD_NAVER_SERIES);

    private static final Map<String, List<String>> PLATFORM_NAMES_BY_ROUTER = Map.of(
            STEAM, List.of(PD_STEAM),
            TMDB, List.of(PD_TMDB_MOVIE, PD_TMDB_TV),
            WEBTOON, List.of(PD_NAVER_WEBTOON),
            WEBNOVEL, List.of(PD_NAVER_SERIES));

    private CorpusKeyMapper() { }

    /** platform_data 한 행 → 라우터 키. 매핑이 없거나 ID 가 비면 null. */
    public static CorpusKey toKey(String platformName, String platformSpecificId) {
        if (platformName == null || platformSpecificId == null || platformSpecificId.isBlank()) return null;
        String psid = platformSpecificId.trim();
        return switch (platformName) {
            case PD_STEAM -> new CorpusKey(STEAM, psid);
            case PD_TMDB_MOVIE -> new CorpusKey(TMDB, "movie_" + psid);
            case PD_TMDB_TV -> new CorpusKey(TMDB, "tv_" + psid);
            case PD_NAVER_WEBTOON -> new CorpusKey(WEBTOON, psid);
            case PD_NAVER_SERIES -> new CorpusKey(WEBNOVEL, psid);
            default -> null;
        };
    }

    /** 라우터 키 → (platformName, platformSpecificId). 매핑이 없으면 null. */
    public static PlatformRef toPlatformRef(CorpusKey corpusKey) {
        if (corpusKey == null || corpusKey.platform() == null
                || corpusKey.key() == null || corpusKey.key().isBlank()) {
            return null;
        }
        String key = corpusKey.key();
        return switch (corpusKey.platform()) {
            case STEAM -> new PlatformRef(PD_STEAM, key);
            case TMDB -> tmdbRef(key);
            case WEBTOON -> new PlatformRef(PD_NAVER_WEBTOON, key);
            case WEBNOVEL -> new PlatformRef(PD_NAVER_SERIES, key);
            default -> null;
        };
    }

    private static PlatformRef tmdbRef(String key) {
        if (key.startsWith("movie_") && key.length() > 6) return new PlatformRef(PD_TMDB_MOVIE, key.substring(6));
        if (key.startsWith("tv_") && key.length() > 3) return new PlatformRef(PD_TMDB_TV, key.substring(3));
        return null;   // 접두어가 없는 TMDB 키는 영화·시리즈를 구분할 수 없다
    }

    /** 라우터 platform → 조회에 쓸 platform_data.platformName 목록. 모르는 값이면 빈 목록. */
    public static List<String> platformNamesOf(String routerPlatform) {
        if (routerPlatform == null) return List.of();
        return PLATFORM_NAMES_BY_ROUTER.getOrDefault(routerPlatform, List.of());
    }

    /** rec_chain.skipped_keys 저장 형식 "platform:key". */
    public static String flat(CorpusKey corpusKey) {
        return corpusKey.platform() + ":" + corpusKey.key();
    }

    /** flat 형식 되돌리기. 형식이 아니면 null (저장된 값이 상해도 요청을 실패시키지 않는다). */
    public static CorpusKey parseFlat(String flat) {
        if (flat == null) return null;
        int i = flat.indexOf(':');
        if (i <= 0 || i == flat.length() - 1) return null;
        return new CorpusKey(flat.substring(0, i), flat.substring(i + 1));
    }

    /** platform_data 한 행을 가리키는 좌표. */
    public record PlatformRef(String platformName, String platformSpecificId) { }
}
