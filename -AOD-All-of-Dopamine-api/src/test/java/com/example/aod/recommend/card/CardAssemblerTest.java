package com.example.AOD.recommend.card;

import com.example.AOD.recommend.key.CorpusKey;
import com.example.AOD.recommend.key.CorpusKeyService;
import com.example.AOD.recommend.router.RouterText;
import com.example.AOD.recommend.router.dto.RouterItem;
import com.example.AOD.recommend.router.dto.RouterScore;
import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CardAssemblerTest {

    private final CorpusKeyService corpusKeyService = mock(CorpusKeyService.class);
    private final CardAssembler assembler = new CardAssembler(corpusKeyService);

    private static Content content(long id, boolean adult) {
        Content c = new Content();
        c.setContentId(id);
        c.setDomain(Domain.GAME);
        c.setMasterTitle("작품 " + id);
        c.setIsAdult(adult);
        return c;
    }

    private static RouterItem item(String platform, String key, int rank) {
        return new RouterItem(platform, key, rank, "730", "content_sim", false, 1.0,
                new RouterScore(1.0, 0.5, Map.of()), "steam.v1");
    }

    @Test
    void keepsRouterOrderAndFillsUpToSize() {
        given(corpusKeyService.contentsByKey(anyCollection())).willReturn(Map.of(
                new CorpusKey("steam", "1"), content(11L, false),
                new CorpusKey("steam", "2"), content(12L, false),
                new CorpusKey("steam", "3"), content(13L, false)));

        Assembly assembly = assembler.assemble(
                List.of(item("steam", "1", 0), item("steam", "2", 1), item("steam", "3", 2)), 2, Set.of());

        assertEquals(List.of(11L, 12L), assembly.cards().stream().map(c -> c.content().getContentId()).toList());
        assertEquals(1, assembly.dropped().size());
        assertEquals(DroppedCandidate.OVER_K, assembly.dropped().get(0).reason());
        assertEquals("3", assembly.dropped().get(0).item().key());
        assertEquals(13L, assembly.dropped().get(0).contentId(), "정원 초과는 작품을 알고 있다");
    }

    @Test
    void recordsMissingAndAdultCandidates() {
        given(corpusKeyService.contentsByKey(anyCollection())).willReturn(Map.of(
                new CorpusKey("steam", "1"), content(11L, false),
                new CorpusKey("steam", "3"), content(13L, true)));

        Assembly assembly = assembler.assemble(
                List.of(item("steam", "1", 0), item("steam", "2", 1), item("steam", "3", 2)), 20, Set.of());

        assertEquals(List.of(11L), assembly.cards().stream().map(c -> c.content().getContentId()).toList());
        assertEquals(List.of("2", "3"), assembly.dropped().stream().map(d -> d.item().key()).toList());
        assertEquals(List.of(DroppedCandidate.NOT_IN_DB, DroppedCandidate.ADULT),
                assembly.dropped().stream().map(DroppedCandidate::reason).toList());
        assertEquals(null, assembly.dropped().get(0).contentId(), "DB 에 없으면 content_id 가 없다");
        assertEquals(13L, assembly.dropped().get(1).contentId());
    }

    @Test
    void skipsWorksAlreadySeenOrRepeatedAcrossPlatforms() {
        Content shared = content(30L, false);
        given(corpusKeyService.contentsByKey(anyCollection())).willReturn(Map.of(
                new CorpusKey("steam", "1"), content(11L, false),
                new CorpusKey("steam", "2"), shared,
                new CorpusKey("tmdb", "movie_9"), shared));

        Assembly assembly = assembler.assemble(
                List.of(item("steam", "1", 0), item("steam", "2", 1), item("tmdb", "movie_9", 2)),
                20, Set.of(11L));

        assertEquals(List.of(30L), assembly.cards().stream().map(c -> c.content().getContentId()).toList());
        assertEquals(List.of(DroppedCandidate.DUP_CONTENT, DroppedCandidate.DUP_CONTENT),
                assembly.dropped().stream().map(DroppedCandidate::reason).toList());
    }

    @Test
    void emptyInputDoesNotQuery() {
        Assembly assembly = assembler.assemble(List.of(), 20, Set.of());

        assertTrue(assembly.cards().isEmpty());
        assertTrue(assembly.dropped().isEmpty());
        verify(corpusKeyService, never()).contentsByKey(anyCollection());
    }

    @Test
    void candidatesWithUnusableKeysAreThrownAwayEntirely() {
        String withNul = "bad\0key";
        String tooLong = "x".repeat(RouterText.MAX_LENGTH + 1);
        given(corpusKeyService.contentsByKey(anyCollection()))
                .willReturn(Map.of(new CorpusKey("steam", "1"), content(11L, false)));

        Assembly assembly = assembler.assemble(List.of(
                item("steam", "1", 0),
                item("steam", withNul, 1),
                item("steam", tooLong, 2),
                item("steam\tx", "2", 3),
                item("steam", "", 4),
                item(null, null, 5)), 20, Set.of());

        assertEquals(List.of(11L), assembly.cards().stream().map(c -> c.content().getContentId()).toList());
        // 버린 후보로도 남기지 않는다 — 남기면 그 키가 로그(jsonb·text[])와 체인 배열로 그대로 흘러간다.
        assertTrue(assembly.dropped().isEmpty(), "쓸 수 없는 키는 파이프라인에 들이지 않는다");
    }

    @Test
    void sizeZeroDropsEverythingAsOverK() {
        given(corpusKeyService.contentsByKey(anyCollection()))
                .willReturn(Map.of(new CorpusKey("steam", "1"), content(11L, false)));

        Assembly assembly = assembler.assemble(List.of(item("steam", "1", 0)), 0, Set.of());

        assertTrue(assembly.cards().isEmpty());
        assertEquals(DroppedCandidate.OVER_K, assembly.dropped().get(0).reason());
    }
}
