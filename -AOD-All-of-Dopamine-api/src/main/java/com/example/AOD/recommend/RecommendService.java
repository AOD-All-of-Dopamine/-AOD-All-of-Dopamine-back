package com.example.AOD.recommend;

import com.example.AOD.recommend.candidate.AiAssetRepository;
import com.example.AOD.recommend.candidate.Candidate;
import com.example.AOD.recommend.candidate.CandidateGenerator;
import com.example.AOD.recommend.dto.QualityScore;
import com.example.AOD.recommend.dto.RecRequest;
import com.example.AOD.recommend.dto.RecommendationItem;
import com.example.AOD.recommend.dto.ScoreBreakdown;
import com.example.AOD.recommend.feature.FeatureCalculator;
import com.example.AOD.recommend.feature.FeatureVector;
import com.example.AOD.recommend.log.RecLogWriter;
import com.example.AOD.recommend.postprocess.PostProcessor;
import com.example.AOD.recommend.rank.Ranker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RecommendService {

    private static final int POOL_SIZE = 1000;      // spec §5.1 500~1000
    private static final double RECENCY_BOOST_MAX = 0.08; // spec §5.4-2 recency boost ≤8%

    private final CandidateGenerator candidateGenerator;
    private final AiAssetRepository aiAssetRepository;
    private final FeatureCalculator featureCalculator;
    private final Ranker ranker;
    private final PostProcessor postProcessor;
    private final RecLogWriter recLogWriter;
    private final ObjectMapper objectMapper;

    public RecommendService(CandidateGenerator candidateGenerator, AiAssetRepository aiAssetRepository,
                            FeatureCalculator featureCalculator, Ranker ranker, PostProcessor postProcessor,
                            RecLogWriter recLogWriter, ObjectMapper objectMapper) {
        this.candidateGenerator = candidateGenerator;
        this.aiAssetRepository = aiAssetRepository;
        this.featureCalculator = featureCalculator;
        this.ranker = ranker;
        this.postProcessor = postProcessor;
        this.recLogWriter = recLogWriter;
        this.objectMapper = objectMapper;
    }

    @Cacheable(value = "homeRecommendations",
               key = "#req.userId() + ':' + #req.page()",
               condition = "#req.location() == 'home'")
    public List<RecommendationItem> recommend(RecRequest req) {
        // M2: 무조건 콜드스타트(quality/popularity fallback). positive_count 분기·온보딩 시드는 M3.
        List<Long> ids = candidateGenerator.generate(null, new String[0], null, new String[0], POOL_SIZE);
        List<Candidate> pool = loadCandidates(ids);

        LocalDate now = LocalDate.now();
        for (Candidate c : pool) {
            FeatureVector f = new FeatureVector(
                    0.0, // cold-start: fun_tag 프로파일 없음
                    0.0, // cold-start: profile vector 없음
                    featureCalculator.qualityPopularityScore(c.quality),
                    0.0, // cold-start: profile 장르 없음(metadata=0)
                    featureCalculator.recencyScore(c.releaseDate, now));
            c.features = f;
            c.score = ranker.homeScore(f);
        }

        // spec §5.4 순서: hard filter → soft penalty/boost → diversity → Top-N.
        List<Candidate> filtered = postProcessor.hardFilter(pool, false);
        Map<Long, Double> noPenalty = Collections.emptyMap(); // 콜드스타트: 유저 negative 프로파일 없음
        Map<Long, Double> recencyBoost = new HashMap<>();
        for (Candidate c : filtered) {
            recencyBoost.put(c.contentId, c.features.recency() * RECENCY_BOOST_MAX); // §5.4-2 recency boost(콜드스타트에도 적용)
        }
        postProcessor.applySoftAdjust(filtered, noPenalty, recencyBoost);
        List<Candidate> top = postProcessor.diversify(filtered, req.size());

        List<RecommendationItem> items = toItems(top);
        recLogWriter.writeImpressions(UUID.randomUUID(), req.userId(), req.location(),
                req.selectedContentId(), items);
        return items;
    }

    private List<Candidate> loadCandidates(List<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyList();
        List<Candidate> out = new ArrayList<>();
        for (Object[] r : aiAssetRepository.loadCandidateRows(ids)) {
            Candidate c = new Candidate(((Number) r[0]).longValue(), (String) r[1]);
            c.releaseDate = r[2] == null ? null : ((Date) r[2]).toLocalDate();
            double qp = r[3] == null ? 0.0 : ((Number) r[3]).doubleValue();
            c.quality = new QualityScore(0, 0, 0, 0, qp);
            c.candidateSource = "quality";
            out.add(c);
        }
        return out;
    }

    private List<RecommendationItem> toItems(List<Candidate> top) {
        List<RecommendationItem> items = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            Candidate c = top.get(i);
            FeatureVector f = c.features;
            ScoreBreakdown sb = new ScoreBreakdown(
                    f.funTag(), f.profileSim(), f.quality(), f.metadata(), f.recency(), c.score);
            String json;
            try {
                json = objectMapper.writeValueAsString(sb);
            } catch (JsonProcessingException e) {
                json = "{}";
            }
            items.add(new RecommendationItem(c.contentId, c.domain, c.score, c.candidateSource, i, json));
        }
        return items;
    }
}
