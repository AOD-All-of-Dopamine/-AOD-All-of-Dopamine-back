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

import java.time.Instant;
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
    private MovieContentRepository movieRepo;
    private IngestPipeline pipeline;

    private static final String KAKAO_V4 = """
            platformName: KakaoPage
            domain: WEBNOVEL
            mappings:
              title: master.masterTitle
              synopsis: master.synopsis
              author: domain.author
              genres: master.genres
              seriesId: platform.platformSpecificId
            """;

    private static final String TMDB_MOVIE_V4 = """
            platformName: TMDB_MOVIE
            domain: MOVIE
            mappings:
              title: master.masterTitle
              overview: master.synopsis
              movie_details.id: platform.platformSpecificId
            """;

    @BeforeEach
    void setUp() {
        rawRepo = mock(RawItemRepository.class);
        runRepo = mock(TransformRunRepository.class);
        contentRepo = mock(ContentRepository.class);
        platformRepo = mock(PlatformDataRepository.class);
        webnovelRepo = mock(WebnovelContentRepository.class);
        movieRepo = mock(MovieContentRepository.class);
        DomainCatalog catalog = new DomainCatalog(
                movieRepo, mock(TvContentRepository.class),
                mock(GameContentRepository.class), mock(WebtoonContentRepository.class), webnovelRepo);
        RuleRegistry registry = mock(RuleRegistry.class);
        when(registry.resolve("WEBNOVEL", "KakaoPage"))
                .thenReturn(PlatformRule.parse("t", new Yaml().load(KAKAO_V4)));
        when(registry.pathOf("KakaoPage")).thenReturn("rules/webnovel/kakaopage.yml");
        when(registry.resolve("MOVIE", "TMDB_MOVIE"))
                .thenReturn(PlatformRule.parse("t", new Yaml().load(TMDB_MOVIE_V4)));
        when(registry.pathOf("TMDB_MOVIE")).thenReturn("rules/movie/tmdb_movie.yml");
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

        // 도메인 필드: 매핑된 프로퍼티 '덮어쓰기', 단 platforms는 기존∪신규 합집합
        verify(webnovelRepo).save(existingNovel);
        assertEquals(List.of("판타지"), existing.getGenres());          // genres는 마스터로 승격 (덮어쓰기 시맨틱)
        assertEquals(List.of("NaverSeries", "KakaoPage"), existingNovel.getPlatforms()); // 크로스플랫폼 유실 방지

        ArgumentCaptor<TransformRun> run = ArgumentCaptor.forClass(TransformRun.class);
        verify(runRepo).save(run.capture());
        assertEquals("SUCCESS", run.getValue().getStatus());
        assertEquals(100L, run.getValue().getProducedContentId());
    }

    @Test
    void genresOverwriteOnReingestButEmptyCollectKeepsExisting() {     // genres 승격 후 병합 시맨틱 핀
        Content existing = new Content();
        existing.setContentId(100L);
        existing.setDomain(Domain.WEBNOVEL);
        existing.setMasterTitle("전지적 독자 시점");
        existing.setGenres(List.of("무협"));                            // 이전 수집의 장르
        WebnovelContent existingNovel = new WebnovelContent(existing);
        existingNovel.setAuthor("싱숑");
        when(webnovelRepo.findByAuthor("싱숑")).thenReturn(List.of(existingNovel));
        when(webnovelRepo.findById(100L)).thenReturn(Optional.of(existingNovel));
        when(platformRepo.findByPlatformNameAndPlatformSpecificId(any(), any()))
                .thenReturn(Optional.of(new PlatformData()));

        RawItem withGenres = kakaoRaw(20L, Map.of(
                "title", "전지적 독자 시점", "author", "싱숑", "seriesId", "K-1",
                "genres", List.of("판타지")));
        RawItem withoutGenres = kakaoRaw(21L, Map.of(
                "title", "전지적 독자 시점", "author", "싱숑", "seriesId", "K-2"));
        when(rawRepo.lockNextBatch(10)).thenReturn(List.of(withGenres, withoutGenres));

        pipeline.processBatch(10);

        assertEquals(List.of("판타지"), existing.getGenres());          // 재수집 = 덮어쓰기, 빈 수집 = 유지
    }

    @Test
    void existingPlatformDataIsRefreshedNotDuplicated() {              // 리뷰 Critical: 재수집 신선도 반영
        Content existing = new Content();
        existing.setContentId(100L);
        existing.setDomain(Domain.WEBNOVEL);
        existing.setMasterTitle("전지적 독자 시점");
        WebnovelContent existingNovel = new WebnovelContent(existing);
        existingNovel.setAuthor("싱숑");
        when(webnovelRepo.findByAuthor("싱숑")).thenReturn(List.of(existingNovel));
        when(webnovelRepo.findById(100L)).thenReturn(Optional.of(existingNovel));

        PlatformData prior = new PlatformData();                       // 이미 존재하는 행
        prior.setPlatformName("KakaoPage");
        prior.setPlatformSpecificId("K-77");
        prior.setContent(existing);
        prior.setLastSeenAt(Instant.EPOCH);                            // 갱신 여부 확인용
        when(platformRepo.findByPlatformNameAndPlatformSpecificId("KakaoPage", "K-77"))
                .thenReturn(Optional.of(prior));

        RawItem r = kakaoRaw(6L, Map.of("title", "전지적 독자 시점", "author", "싱숑", "seriesId", "K-77"));
        when(rawRepo.lockNextBatch(10)).thenReturn(List.of(r));

        pipeline.processBatch(10);

        verify(platformRepo, times(1)).save(same(prior));              // 같은 행 갱신 — 두 번째 insert 없음
        assertTrue(prior.getLastSeenAt().isAfter(Instant.EPOCH));      // lastSeenAt 갱신됨
        assertSame(existing, prior.getContent());                      // content 포인터 불변
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

    @Test
    void movieReingestBySamePlatformIdMergesInsteadOfFailing() {       // 리뷰 Critical 핀: 중복후보 없는 도메인
        Content existing = new Content();
        existing.setContentId(200L);
        existing.setDomain(Domain.MOVIE);
        existing.setMasterTitle("듄");
        existing.setSynopsis(null);                                    // null-fill 대상
        MovieContent existingMovie = new MovieContent(existing);
        PlatformData prior = new PlatformData();
        prior.setPlatformName("TMDB_MOVIE");
        prior.setPlatformSpecificId("693134");
        prior.setContent(existing);

        when(platformRepo.findByPlatformNameAndPlatformSpecificId("TMDB_MOVIE", "693134"))
                .thenReturn(Optional.of(prior));
        when(movieRepo.findById(200L)).thenReturn(Optional.of(existingMovie));

        RawItem r = new RawItem();
        r.setRawId(11L);
        r.setPlatformName("TMDB_MOVIE");
        r.setDomain("MOVIE");
        r.setSourcePayload(Map.of("title", "듄", "overview", "새 줄거리",
                "movie_details", Map.of("id", 693134)));
        when(rawRepo.lockNextBatch(10)).thenReturn(List.of(r));

        pipeline.processBatch(10);

        ArgumentCaptor<TransformRun> run = ArgumentCaptor.forClass(TransformRun.class);
        verify(runRepo).save(run.capture());
        assertEquals("SUCCESS", run.getValue().getStatus());           // 재수집 = 같은 작품 갱신 (FAILED 아님)
        assertEquals(200L, run.getValue().getProducedContentId());

        verify(contentRepo, times(1)).save(any());                     // 신규 Content 저장 없음
        verify(contentRepo).save(same(existing));                      // null-fill 병합
        assertEquals("새 줄거리", existing.getSynopsis());
        verify(platformRepo).save(same(prior));                        // 같은 PD 행 갱신 (uk_platform_id 위반 없음)
    }

    @Test
    void blankRawPsidFallsThroughToPayloadKey() {                      // blank 폴백 패리티 핀
        RawItem r = kakaoRaw(12L, Map.of(
                "title", "신작소설",
                "author", "무명",
                "platformSpecificId", "P-9"));                         // seriesId 없음 → payload 공용 키 폴백
        r.setPlatformSpecificId("  ");                                 // raw 컬럼 blank
        when(rawRepo.lockNextBatch(10)).thenReturn(List.of(r));
        when(webnovelRepo.findByAuthor("무명")).thenReturn(List.of()); // 중복후보 없음
        when(platformRepo.findByPlatformNameAndPlatformSpecificId("KakaoPage", "P-9"))
                .thenReturn(Optional.empty());                         // 동일성 라우팅도 미적중 → 신규 경로
        when(contentRepo.save(any())).thenAnswer(inv -> {
            Content c = inv.getArgument(0);
            c.setContentId(300L);
            return c;
        });

        pipeline.processBatch(10);

        ArgumentCaptor<PlatformData> savedPd = ArgumentCaptor.forClass(PlatformData.class);
        verify(platformRepo).save(savedPd.capture());
        assertEquals("P-9", savedPd.getValue().getPlatformSpecificId());
    }

    @Test
    void missingDomainRowSkipsDomainMergeWithoutThrow() {              // 도메인 행 부재 = WARN 후 스킵
        Content existing = new Content();
        existing.setContentId(100L);
        existing.setDomain(Domain.WEBNOVEL);
        existing.setMasterTitle("전지적 독자 시점");
        WebnovelContent existingNovel = new WebnovelContent(existing);
        existingNovel.setAuthor("싱숑");
        when(webnovelRepo.findByAuthor("싱숑")).thenReturn(List.of(existingNovel));
        when(webnovelRepo.findById(100L)).thenReturn(Optional.empty()); // 도메인 행 없음
        when(platformRepo.findByPlatformNameAndPlatformSpecificId("KakaoPage", "K-77"))
                .thenReturn(Optional.empty());

        RawItem r = kakaoRaw(13L, Map.of("title", "전지적 독자 시점", "author", "싱숑", "seriesId", "K-77"));
        when(rawRepo.lockNextBatch(10)).thenReturn(List.of(r));

        pipeline.processBatch(10);

        ArgumentCaptor<TransformRun> run = ArgumentCaptor.forClass(TransformRun.class);
        verify(runRepo).save(run.capture());
        assertEquals("SUCCESS", run.getValue().getStatus());           // 예외 없이 완료
        verify(webnovelRepo, never()).save(any());                     // 도메인 병합만 스킵
    }
}
