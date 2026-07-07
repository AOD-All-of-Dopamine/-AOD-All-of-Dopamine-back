package com.example.AOD.recommend;

import com.example.AOD.recommend.candidate.CandidateGenerator;
import com.example.AOD.recommend.candidate.AiAssetRepository;
import com.example.AOD.recommend.dto.RecRequest;
import com.example.AOD.recommend.dto.RecommendationItem;
import com.example.AOD.recommend.feature.FeatureCalculator;
import com.example.AOD.recommend.log.RecLogWriter;
import com.example.AOD.recommend.postprocess.PostProcessor;
import com.example.AOD.recommend.rank.Ranker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RecommendServiceTest {

    private final CandidateGenerator gen = mock(CandidateGenerator.class);
    private final AiAssetRepository repo = mock(AiAssetRepository.class);
    private final RecLogWriter log = mock(RecLogWriter.class);
    private final RecommendService service = new RecommendService(
            gen, repo, new FeatureCalculator(), new Ranker(), new PostProcessor(), log, new ObjectMapper());

    @Test
    void coldStartRanksByQualityAppliesSoftBoostAndLogsImpressions() {
        Date today = Date.valueOf(LocalDate.now());
        when(gen.generate(isNull(), any(String[].class), isNull(), any(String[].class), anyInt()))
                .thenReturn(List.of(1L, 2L, 3L));
        when(repo.loadCandidateRows(anyList())).thenReturn(List.of(
                new Object[]{1L, "MOVIE", today, 0.9f},
                new Object[]{2L, "GAME", today, 0.5f},
                new Object[]{3L, "TV", today, 0.7f}));

        RecRequest req = new RecRequest("home", null, 0, 20, null);
        List<RecommendationItem> out = service.recommend(req);

        assertEquals(List.of(1L, 3L, 2L), out.stream().map(RecommendationItem::contentId).toList());
        assertEquals(0, out.get(0).rankPosition());
        assertEquals("quality", out.get(0).candidateSource());
        assertTrue(out.get(0).scoreBreakdownJson().contains("quality"));
        // §5.4-2 soft adjust 실제 적용 확인: homeScore(0.15*0.9+0.05*1.0=0.185) + recency_boost 8% = 0.185*1.08.
        // 허용치 1e-7: quality_popularity_score는 PG real(float4) → JDBC Float → double 변환 오차(~4e-9) 감안.
        assertEquals(0.185 * 1.08, out.get(0).score(), 1e-7);
        verify(repo, never()).findPositiveCount(anyLong()); // M2 무조건 콜드스타트 → 분기 조회 안함
        verify(log).writeImpressions(any(), isNull(), eq("home"), isNull(), anyList());
    }
}
