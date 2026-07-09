package com.example.crawler.ingest;

import com.example.shared.entity.*;
import com.example.shared.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DomainCatalogTest {

    private MovieContentRepository movieRepo;
    private TvContentRepository tvRepo;
    private GameContentRepository gameRepo;
    private WebtoonContentRepository webtoonRepo;
    private WebnovelContentRepository webnovelRepo;
    private DomainCatalog catalog;

    @BeforeEach
    void setUp() {
        movieRepo = mock(MovieContentRepository.class);
        tvRepo = mock(TvContentRepository.class);
        gameRepo = mock(GameContentRepository.class);
        webtoonRepo = mock(WebtoonContentRepository.class);
        webnovelRepo = mock(WebnovelContentRepository.class);
        catalog = new DomainCatalog(movieRepo, tvRepo, gameRepo, webtoonRepo, webnovelRepo);
    }

    @Test
    void createReturnsMatchingEntityTypeLinkedToContent() {
        Content c = new Content();
        assertInstanceOf(MovieContent.class, catalog.create(Domain.MOVIE, c));
        assertInstanceOf(TvContent.class, catalog.create(Domain.TV, c));
        assertInstanceOf(GameContent.class, catalog.create(Domain.GAME, c));
        assertInstanceOf(WebtoonContent.class, catalog.create(Domain.WEBTOON, c));
        WebnovelContent w = (WebnovelContent) catalog.create(Domain.WEBNOVEL, c);
        assertSame(c, w.getContent());
    }

    @Test
    void duplicateCandidatesByAuthorOrDeveloperAndUnsupportedDomainsReturnEmpty() {
        Content owner = new Content();
        WebnovelContent existing = new WebnovelContent(owner);
        WebnovelContent probe = new WebnovelContent(new Content());
        probe.setAuthor("싱숑");
        when(webnovelRepo.findByAuthor("싱숑")).thenReturn(List.of(existing));
        assertEquals(List.of(owner), catalog.duplicateCandidates(Domain.WEBNOVEL, probe));

        GameContent game = new GameContent(new Content());
        game.setDeveloper("밸브");
        when(gameRepo.findByDeveloper("밸브")).thenReturn(List.of());
        assertEquals(List.of(), catalog.duplicateCandidates(Domain.GAME, game));

        // author/developer 없으면 검색 안 함 (기존 동작)
        assertEquals(List.of(), catalog.duplicateCandidates(Domain.WEBNOVEL, new WebnovelContent(new Content())));
        verify(webnovelRepo).findByAuthor("싱숑"); // 위의 정상 검색 1회만 있었고,
        verifyNoMoreInteractions(webnovelRepo);   // author 없는 호출은 리포지토리를 건드리지 않았다

        // MOVIE/TV는 중복검사 미지원 유지
        assertEquals(List.of(), catalog.duplicateCandidates(Domain.MOVIE, new MovieContent(new Content())));
        assertEquals(List.of(), catalog.duplicateCandidates(Domain.TV, new TvContent(new Content())));
    }

    @Test
    void webtoonCandidatesAndGameCreateLink() {
        Content owner = new Content();
        WebtoonContent existingToon = new WebtoonContent(owner);
        WebtoonContent probe = new WebtoonContent(new Content());
        probe.setAuthor("비가");
        when(webtoonRepo.findByAuthor("비가")).thenReturn(List.of(existingToon));
        assertEquals(List.of(owner), catalog.duplicateCandidates(Domain.WEBTOON, probe));

        Content c = new Content();
        GameContent g = (GameContent) catalog.create(Domain.GAME, c);
        assertSame(c, g.getContent());
    }

    @Test
    void saveDelegatesToDomainRepositoryAndFindByContentIdLoads() {
        WebnovelContent w = new WebnovelContent(new Content());
        catalog.save(Domain.WEBNOVEL, w);
        verify(webnovelRepo).save(w);

        when(gameRepo.findById(7L)).thenReturn(Optional.of(new GameContent(new Content())));
        assertTrue(catalog.findByContentId(Domain.GAME, 7L).isPresent());
        when(movieRepo.findById(9L)).thenReturn(Optional.empty());
        assertTrue(catalog.findByContentId(Domain.MOVIE, 9L).isEmpty());
    }
}
