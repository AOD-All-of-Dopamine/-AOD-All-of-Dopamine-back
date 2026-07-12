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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IngestPipelineTest {

    private RawItemRepository rawRepo;
    private TransformRunRepository runRepo;
    private RuleRegistry registry;
    private ContentRepository contentRepo;
    private PlatformDataRepository platformRepo;
    private WebnovelContentRepository webnovelRepo;
    private DomainCatalog catalog;
    private IngestPipeline pipeline;

    private static final String NAVER_SERIES_V4 = """
            platformName: NaverSeries
            domain: WEBNOVEL
            mappings:
              title: master.masterTitle
              author: domain.author
              titleId: platform.platformSpecificId
            """;

    @BeforeEach
    void setUp() {
        rawRepo = mock(RawItemRepository.class);
        runRepo = mock(TransformRunRepository.class);
        contentRepo = mock(ContentRepository.class);
        platformRepo = mock(PlatformDataRepository.class);
        webnovelRepo = mock(WebnovelContentRepository.class);
        catalog = new DomainCatalog(
                mock(MovieContentRepository.class), mock(TvContentRepository.class),
                mock(GameContentRepository.class), mock(WebtoonContentRepository.class), webnovelRepo);
        registry = mock(RuleRegistry.class);
        PlatformRule rule = PlatformRule.parse("t", new Yaml().load(NAVER_SERIES_V4));
        when(registry.resolve("WEBNOVEL", "NaverSeries")).thenReturn(rule);
        when(registry.pathOf("NaverSeries")).thenReturn("rules/webnovel/naverseries.yml");

        PlatformTransactionManager ptm = mock(PlatformTransactionManager.class);
        when(ptm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        pipeline = new IngestPipeline(rawRepo, runRepo, registry, new DraftAssembler(catalog),
                catalog, contentRepo, platformRepo, new TransactionTemplate(ptm));
    }

    private RawItem raw(long id, String platform, String domain, Map<String, Object> payload) {
        RawItem r = new RawItem();
        r.setRawId(id);
        r.setPlatformName(platform);
        r.setDomain(domain);
        r.setSourcePayload(payload);
        return r;
    }

    @Test
    void newContentPathSavesAllThreeAndRecordsSuccess() {
        RawItem r = raw(1L, "NaverSeries", "WEBNOVEL",
                Map.of("title", "신작", "author", "작가", "titleId", "42"));
        when(rawRepo.lockNextBatch(10)).thenReturn(List.of(r));
        when(webnovelRepo.findByAuthor("작가")).thenReturn(List.of());
        when(contentRepo.save(any())).thenAnswer(inv -> {
            Content c = inv.getArgument(0);
            c.setContentId(100L);
            return c;
        });
        when(platformRepo.findByPlatformNameAndPlatformSpecificId("NaverSeries", "42"))
                .thenReturn(java.util.Optional.empty());

        int processed = pipeline.processBatch(10);

        assertEquals(1, processed);                       // 반환값 = claim된 건수
        assertTrue(r.isProcessed());                      // claim 시점에 processed 마킹
        verify(contentRepo).save(any(Content.class));
        verify(webnovelRepo).save(any(WebnovelContent.class));
        verify(platformRepo).save(any(PlatformData.class));

        ArgumentCaptor<TransformRun> run = ArgumentCaptor.forClass(TransformRun.class);
        verify(runRepo).save(run.capture());
        assertEquals("SUCCESS", run.getValue().getStatus());
        assertEquals(100L, run.getValue().getProducedContentId());
        assertEquals("rules/webnovel/naverseries.yml", run.getValue().getRulePath());
    }

    @Test
    void blankTitleIsSkippedExplicitly() {                // 개선 ③
        RawItem r = raw(2L, "NaverSeries", "WEBNOVEL", Map.of("author", "작가"));
        when(rawRepo.lockNextBatch(10)).thenReturn(List.of(r));

        pipeline.processBatch(10);

        ArgumentCaptor<TransformRun> run = ArgumentCaptor.forClass(TransformRun.class);
        verify(runRepo).save(run.capture());
        assertEquals("SKIPPED", run.getValue().getStatus());
        verify(contentRepo, never()).save(any());
    }

    @Test
    void unknownPlatformIsFailedAndBatchContinues() {     // 개선 ①+②
        RawItem bad = raw(3L, "NoSuchPlatform", "WEBNOVEL", Map.of("title", "x"));
        RawItem good = raw(4L, "NaverSeries", "WEBNOVEL", Map.of("title", "신작2", "author", "작가2"));
        when(registry.resolve("WEBNOVEL", "NoSuchPlatform"))
                .thenThrow(new IllegalArgumentException("No rule for platform: NoSuchPlatform"));
        when(rawRepo.lockNextBatch(10)).thenReturn(List.of(bad, good));
        when(webnovelRepo.findByAuthor("작가2")).thenReturn(List.of());
        when(contentRepo.save(any())).thenAnswer(inv -> {
            Content c = inv.getArgument(0);
            c.setContentId(101L);
            return c;
        });

        pipeline.processBatch(10);

        ArgumentCaptor<TransformRun> runs = ArgumentCaptor.forClass(TransformRun.class);
        verify(runRepo, times(2)).save(runs.capture());
        assertEquals("FAILED", runs.getAllValues().get(0).getStatus());
        assertTrue(runs.getAllValues().get(0).getError().contains("NoSuchPlatform"));
        assertEquals("SUCCESS", runs.getAllValues().get(1).getStatus()); // 실패해도 배치 계속
        assertTrue(bad.isProcessed());                     // 재시도 무의미 → processed 유지
    }
}
