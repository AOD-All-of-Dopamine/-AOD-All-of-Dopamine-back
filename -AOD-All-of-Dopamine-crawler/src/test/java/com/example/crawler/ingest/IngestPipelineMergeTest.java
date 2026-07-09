package com.example.crawler.ingest;

import com.example.crawler.ingest.rule.PlatformRule;
import com.example.crawler.ingest.rule.RuleRegistry;
import com.example.shared.entity.*;
import com.example.shared.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IngestPipelineMergeTest {

    private RawItemRepository rawRepo;
    private TransformRunRepository runRepo;
    private ContentRepository contentRepo;
    private PlatformDataRepository platformRepo;
    private WebnovelContentRepository webnovelRepo;
    private IngestPipeline pipeline;

    private static final String KAKAO_V4 = """
            platformName: KakaoPage
            domain: WEBNOVEL
            mappings:
              title: master.masterTitle
              synopsis: master.synopsis
              author: domain.author
              genres: domain.genres
              seriesId: platform.platformSpecificId
            """;

    @BeforeEach
    void setUp() {
        rawRepo = mock(RawItemRepository.class);
        runRepo = mock(TransformRunRepository.class);
        contentRepo = mock(ContentRepository.class);
        platformRepo = mock(PlatformDataRepository.class);
        webnovelRepo = mock(WebnovelContentRepository.class);
        DomainCatalog catalog = new DomainCatalog(
                mock(MovieContentRepository.class), mock(TvContentRepository.class),
                mock(GameContentRepository.class), mock(WebtoonContentRepository.class), webnovelRepo);
        RuleRegistry registry = mock(RuleRegistry.class);
        when(registry.resolve("WEBNOVEL", "KakaoPage"))
                .thenReturn(PlatformRule.parse("t", new Yaml().load(KAKAO_V4)));
        when(registry.pathOf("KakaoPage")).thenReturn("rules/webnovel/kakaopage.yml");
        PlatformTransactionManager ptm = mock(PlatformTransactionManager.class);
        when(ptm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        pipeline = new IngestPipeline(rawRepo, runRepo, registry, new DraftAssembler(catalog),
                catalog, contentRepo, platformRepo, new TransactionTemplate(ptm));
    }

    private RawItem kakaoRaw(long id, Map<String, Object> payload) {
        RawItem r = new RawItem();
        r.setRawId(id);
        r.setPlatformName("KakaoPage");
        r.setDomain("WEBNOVEL");
        r.setSourcePayload(payload);
        return r;
    }

    @Test
    void mergesIntoExistingContentPreservingLegacySemantics() {
        // 기존 작품: NaverSeries로 이미 수집된 "전지적 독자 시점"
        Content existing = new Content();
        existing.setContentId(100L);
        existing.setDomain(Domain.WEBNOVEL);
        existing.setMasterTitle("전지적 독자 시점");
        existing.setSynopsis(null);                                   // null-fill 대상
        WebnovelContent existingNovel = new WebnovelContent(existing);
        existingNovel.setAuthor("싱숑");
        existingNovel.setPlatforms(List.of("NaverSeries"));

        when(webnovelRepo.findByAuthor("싱숑")).thenReturn(List.of(existingNovel));
        when(webnovelRepo.findById(100L)).thenReturn(Optional.of(existingNovel));
        when(platformRepo.findByPlatformNameAndPlatformSpecificId("KakaoPage", "K-77"))
                .thenReturn(Optional.empty());

        RawItem r = kakaoRaw(5L, Map.of(
                "title", "전지적  독자 시점",                          // 정규화 후 동일 제목
                "synopsis", "카카오 시놉시스",
                "author", "싱숑",
                "genres", List.of("판타지"),
                "seriesId", "K-77"));
        when(rawRepo.lockNextBatch(10)).thenReturn(List.of(r));

        pipeline.processBatch(10);

        // 신규 Content 저장 없음 — 기존에 병합
        ArgumentCaptor<Content> savedContent = ArgumentCaptor.forClass(Content.class);
        verify(contentRepo).save(savedContent.capture());
        assertSame(existing, savedContent.getValue());
        assertEquals("카카오 시놉시스", existing.getSynopsis());        // null-fill 수행

        // PlatformData는 (platform, psid) 부재 시에만 추가되고 기존 Content에 연결
        ArgumentCaptor<PlatformData> savedPd = ArgumentCaptor.forClass(PlatformData.class);
        verify(platformRepo).save(savedPd.capture());
        assertSame(existing, savedPd.getValue().getContent());

        // 도메인 필드: 매핑된 프로퍼티 '덮어쓰기' (기존 동작 보존 — platforms 교체 이슈 포함)
        verify(webnovelRepo).save(existingNovel);
        assertEquals(List.of("판타지"), existingNovel.getGenres());
        assertEquals(List.of("KakaoPage"), existingNovel.getPlatforms()); // ⚠ 기존 이슈 그대로 (후속 이슈)

        ArgumentCaptor<TransformRun> run = ArgumentCaptor.forClass(TransformRun.class);
        verify(runRepo).save(run.capture());
        assertEquals("SUCCESS", run.getValue().getStatus());
        assertEquals(100L, run.getValue().getProducedContentId());
    }

    @Test
    void existingPlatformDataIsNotDuplicated() {
        Content existing = new Content();
        existing.setContentId(100L);
        existing.setDomain(Domain.WEBNOVEL);
        existing.setMasterTitle("전지적 독자 시점");
        WebnovelContent existingNovel = new WebnovelContent(existing);
        existingNovel.setAuthor("싱숑");
        when(webnovelRepo.findByAuthor("싱숑")).thenReturn(List.of(existingNovel));
        when(webnovelRepo.findById(100L)).thenReturn(Optional.of(existingNovel));
        when(platformRepo.findByPlatformNameAndPlatformSpecificId("KakaoPage", "K-77"))
                .thenReturn(Optional.of(new PlatformData()));          // 이미 존재

        RawItem r = kakaoRaw(6L, Map.of("title", "전지적 독자 시점", "author", "싱숑", "seriesId", "K-77"));
        when(rawRepo.lockNextBatch(10)).thenReturn(List.of(r));

        pipeline.processBatch(10);

        verify(platformRepo, never()).save(any());                     // 중복 추가 안 함 (기존 동작)
    }

    @Test
    void secondItemResolvingToSameContentIsLabeledSuccessDuplicate() { // T6 리뷰 후속
        Content existing = new Content();
        existing.setContentId(100L);
        existing.setDomain(Domain.WEBNOVEL);
        existing.setMasterTitle("전지적 독자 시점");
        WebnovelContent existingNovel = new WebnovelContent(existing);
        existingNovel.setAuthor("싱숑");
        when(webnovelRepo.findByAuthor("싱숑")).thenReturn(List.of(existingNovel));
        when(webnovelRepo.findById(100L)).thenReturn(Optional.of(existingNovel));
        when(platformRepo.findByPlatformNameAndPlatformSpecificId(any(), any()))
                .thenReturn(Optional.of(new PlatformData()));

        RawItem first = kakaoRaw(7L, Map.of("title", "전지적 독자 시점", "author", "싱숑", "seriesId", "K-1"));
        RawItem second = kakaoRaw(8L, Map.of("title", "전지적 독자 시점", "author", "싱숑", "seriesId", "K-2"));
        when(rawRepo.lockNextBatch(10)).thenReturn(List.of(first, second));

        pipeline.processBatch(10);

        ArgumentCaptor<TransformRun> runs = ArgumentCaptor.forClass(TransformRun.class);
        verify(runRepo, times(2)).save(runs.capture());
        assertEquals("SUCCESS", runs.getAllValues().get(0).getStatus());
        assertEquals("SUCCESS_DUPLICATE", runs.getAllValues().get(1).getStatus());
        assertEquals(100L, runs.getAllValues().get(1).getProducedContentId());
    }

    @Test
    void auditSaveFailureDoesNotAbortBatch() {                         // T6 리뷰 후속 (finally 가드)
        Content existing = new Content();
        existing.setContentId(100L);
        existing.setDomain(Domain.WEBNOVEL);
        existing.setMasterTitle("전지적 독자 시점");
        WebnovelContent existingNovel = new WebnovelContent(existing);
        existingNovel.setAuthor("싱숑");
        when(webnovelRepo.findByAuthor("싱숑")).thenReturn(List.of(existingNovel));
        when(webnovelRepo.findById(100L)).thenReturn(Optional.of(existingNovel));
        when(platformRepo.findByPlatformNameAndPlatformSpecificId(any(), any()))
                .thenReturn(Optional.of(new PlatformData()));
        when(runRepo.save(any())).thenThrow(new RuntimeException("audit db down"))
                .thenReturn(null);                                     // 첫 저장만 실패

        RawItem first = kakaoRaw(9L, Map.of("title", "전지적 독자 시점", "author", "싱숑", "seriesId", "K-1"));
        RawItem second = kakaoRaw(10L, Map.of("title", "전지적 독자 시점", "author", "싱숑", "seriesId", "K-2"));
        when(rawRepo.lockNextBatch(10)).thenReturn(List.of(first, second));

        int processed = pipeline.processBatch(10);                     // 예외가 새어나오면 테스트 실패

        assertEquals(2, processed);
        verify(runRepo, times(2)).save(any());                         // 두 번째 item도 계속 처리됨
    }
}
