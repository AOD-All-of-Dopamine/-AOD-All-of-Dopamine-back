package com.example.AOD.recommend.key;

import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.entity.PlatformData;
import com.example.shared.repository.PlatformDataRepository;
import com.example.shared.repository.PlatformKeyRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CorpusKeyServiceTest {

    private final PlatformDataRepository repository = mock(PlatformDataRepository.class);
    private final CorpusKeyService service = new CorpusKeyService(repository);

    private static Content content(long id, boolean adult) {
        Content c = new Content();
        c.setContentId(id);
        c.setDomain(Domain.GAME);
        c.setMasterTitle("작품 " + id);
        c.setIsAdult(adult);
        return c;
    }

    private static PlatformData platformData(long rowId, String platformName, String psid, Content content) {
        PlatformData pd = new PlatformData();
        pd.setPlatformDataId(rowId);
        pd.setPlatformName(platformName);
        pd.setPlatformSpecificId(psid);
        pd.setContent(content);
        return pd;
    }

    @Test
    void mapsContentIdsToKeysAndPrefersLowestPlatformDataId() {
        given(repository.findRecKeyRowsByContentIds(anyCollection(), anyCollection())).willReturn(List.of(
                new PlatformKeyRow(1L, "Steam", "730", 10L),
                new PlatformKeyRow(2L, "TMDB_MOVIE", "603", 20L),
                new PlatformKeyRow(3L, "NaverSeries", "29494", 31L),
                new PlatformKeyRow(3L, "NaverWebtoon", "183559", 30L)));   // 같은 작품, 더 작은 행 id 가 이긴다

        Map<Long, CorpusKey> keys = service.keysByContentId(List.of(1L, 2L, 3L, 4L));

        assertEquals(new CorpusKey("steam", "730"), keys.get(1L));
        assertEquals(new CorpusKey("tmdb", "movie_603"), keys.get(2L));
        assertEquals(new CorpusKey("webtoon", "183559"), keys.get(3L));
        assertTrue(!keys.containsKey(4L), "platform_data 행이 없는 작품은 키가 없다");
    }

    @Test
    void emptyInputSkipsQuery() {
        assertEquals(Map.of(), service.keysByContentId(List.of()));
        assertEquals(Map.of(), service.keysByContentId(null));
        assertEquals(Map.of(), service.contentsByKey(List.of()));
        verify(repository, never()).findRecKeyRowsByContentIds(anyCollection(), anyCollection());
    }

    @Test
    void mapsKeysBackToContentsWithOneQueryPerPlatformName() {
        Content steamGame = content(1L, false);
        Content movie = content(2L, true);
        given(repository.findWithContentByPlatformNameAndIds(eq("Steam"), anyCollection()))
                .willReturn(List.of(platformData(10L, "Steam", "730", steamGame)));
        given(repository.findWithContentByPlatformNameAndIds(eq("TMDB_MOVIE"), anyCollection()))
                .willReturn(List.of(platformData(20L, "TMDB_MOVIE", "603", movie)));

        Map<CorpusKey, Content> byKey = service.contentsByKey(List.of(
                new CorpusKey("steam", "730"),
                new CorpusKey("tmdb", "movie_603"),
                new CorpusKey("tmdb", "movie_999"),      // DB 에 없다
                new CorpusKey("kakao", "1")));           // 매핑 불가 — 조회조차 하지 않는다

        assertEquals(2, byKey.size());
        assertEquals(1L, byKey.get(new CorpusKey("steam", "730")).getContentId());
        assertEquals(2L, byKey.get(new CorpusKey("tmdb", "movie_603")).getContentId(),
                "성인 여부는 여기서 거르지 않는다 — CardAssembler 가 이유와 함께 버린다");
        verify(repository).findWithContentByPlatformNameAndIds(eq("Steam"), anyCollection());
        verify(repository).findWithContentByPlatformNameAndIds(eq("TMDB_MOVIE"), anyCollection());
        verify(repository, never()).findWithContentByPlatformNameAndIds(eq("NaverSeries"), anyCollection());
    }
}
