package com.example.AOD.recommend.key;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CorpusKeyMapperTest {

    @Test
    void mapsEverySupportedPlatform() {
        assertEquals(new CorpusKey("steam", "730"), CorpusKeyMapper.toKey("Steam", "730"));
        assertEquals(new CorpusKey("tmdb", "movie_603"), CorpusKeyMapper.toKey("TMDB_MOVIE", "603"));
        assertEquals(new CorpusKey("tmdb", "tv_1399"), CorpusKeyMapper.toKey("TMDB_TV", "1399"));
        assertEquals(new CorpusKey("webtoon", "183559"), CorpusKeyMapper.toKey("NaverWebtoon", "183559"));
        assertEquals(new CorpusKey("webnovel", "29494"), CorpusKeyMapper.toKey("NaverSeries", "29494"));
    }

    @Test
    void unmappedPlatformOrBlankIdIsNull() {
        assertNull(CorpusKeyMapper.toKey("KakaoPage", "123"));
        assertNull(CorpusKeyMapper.toKey("Epic", "abc"));
        assertNull(CorpusKeyMapper.toKey("Steam", null));
        assertNull(CorpusKeyMapper.toKey("Steam", "   "));
        assertNull(CorpusKeyMapper.toKey(null, "730"));
    }

    @Test
    void roundTripsBackToPlatformData() {
        assertEquals(new CorpusKeyMapper.PlatformRef("Steam", "730"),
                CorpusKeyMapper.toPlatformRef(new CorpusKey("steam", "730")));
        assertEquals(new CorpusKeyMapper.PlatformRef("TMDB_MOVIE", "603"),
                CorpusKeyMapper.toPlatformRef(new CorpusKey("tmdb", "movie_603")));
        assertEquals(new CorpusKeyMapper.PlatformRef("TMDB_TV", "1399"),
                CorpusKeyMapper.toPlatformRef(new CorpusKey("tmdb", "tv_1399")));
        assertEquals(new CorpusKeyMapper.PlatformRef("NaverWebtoon", "183559"),
                CorpusKeyMapper.toPlatformRef(new CorpusKey("webtoon", "183559")));
        assertEquals(new CorpusKeyMapper.PlatformRef("NaverSeries", "29494"),
                CorpusKeyMapper.toPlatformRef(new CorpusKey("webnovel", "29494")));

        assertNull(CorpusKeyMapper.toPlatformRef(new CorpusKey("tmdb", "603")), "접두어 없는 tmdb 키는 매핑 불가");
        assertNull(CorpusKeyMapper.toPlatformRef(new CorpusKey("kakao", "1")));
        assertNull(CorpusKeyMapper.toPlatformRef(null));
    }

    @Test
    void flatFormIsPlatformColonKey() {
        assertEquals("tmdb:movie_603", CorpusKeyMapper.flat(new CorpusKey("tmdb", "movie_603")));
        assertEquals(new CorpusKey("tmdb", "movie_603"), CorpusKeyMapper.parseFlat("tmdb:movie_603"));
        assertEquals(new CorpusKey("steam", "730"), CorpusKeyMapper.parseFlat("steam:730"));
        assertNull(CorpusKeyMapper.parseFlat("steam"));
        assertNull(CorpusKeyMapper.parseFlat(":730"));
        assertNull(CorpusKeyMapper.parseFlat("steam:"));
        assertNull(CorpusKeyMapper.parseFlat(null));
    }

    @Test
    void platformNamesOfMapsRouterPlatformToPlatformDataNames() {
        assertEquals(List.of("Steam"), CorpusKeyMapper.platformNamesOf("steam"));
        assertEquals(List.of("TMDB_MOVIE", "TMDB_TV"), CorpusKeyMapper.platformNamesOf("tmdb"));
        assertEquals(List.of("NaverWebtoon"), CorpusKeyMapper.platformNamesOf("webtoon"));
        assertEquals(List.of("NaverSeries"), CorpusKeyMapper.platformNamesOf("webnovel"));
        assertEquals(List.of(), CorpusKeyMapper.platformNamesOf("kakao"));
        assertEquals(List.of(), CorpusKeyMapper.platformNamesOf(null));
    }

    @Test
    void supportedPlatformNamesCoverExactlyTheFiveMappedOnes() {
        assertEquals(List.of("Steam", "TMDB_MOVIE", "TMDB_TV", "NaverWebtoon", "NaverSeries"),
                CorpusKeyMapper.SUPPORTED_PLATFORM_NAMES);
    }
}
