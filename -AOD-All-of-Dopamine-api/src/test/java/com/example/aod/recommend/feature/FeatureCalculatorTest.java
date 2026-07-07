package com.example.AOD.recommend.feature;

import com.example.AOD.recommend.dto.FunTag;
import com.example.AOD.recommend.dto.QualityScore;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureCalculatorTest {

    private final FeatureCalculator calc = new FeatureCalculator();

    @Test
    void funTagMatchScoreWeightsScoreTimesConfidence() {
        double s = calc.funTagMatchScore(Map.of("힐링", 1.0),
                List.of(new FunTag("힐링", 0.8, 0.5), new FunTag("코믹", 0.9, 0.9)));
        assertEquals(0.40, s, 1e-9); // 1.0 * (0.8*0.5), 미매칭 태그 제외
    }

    @Test
    void funTagMatchScoreZeroForEmptyProfile() {
        assertEquals(0.0, calc.funTagMatchScore(Map.of(),
                List.of(new FunTag("힐링", 0.8, 0.5))), 1e-9);
    }

    @Test
    void profileSimilarityCosine() {
        assertEquals(1.0, calc.profileSimilarityScore(new float[]{1, 0}, new float[]{1, 0}), 1e-9);
        assertEquals(0.0, calc.profileSimilarityScore(new float[]{1, 0}, new float[]{0, 1}), 1e-9);
    }

    @Test
    void metadataMatchIsGenreJaccard() {
        assertEquals(1.0 / 3, calc.metadataMatchScore(Set.of("A", "B"), Set.of("B", "C")), 1e-9);
    }

    @Test
    void qualityLookupReturnsPopularityScoreOrZero() {
        assertEquals(0.7, calc.qualityPopularityScore(new QualityScore(0, 0, 0, 0, 0.7)), 1e-9);
        assertEquals(0.0, calc.qualityPopularityScore(null), 1e-9);
    }

    @Test
    void recencyDecaysToZeroOverOneYear() {
        LocalDate now = LocalDate.of(2026, 7, 3);
        assertEquals(1.0, calc.recencyScore(now, now), 1e-9);
        assertEquals(0.0, calc.recencyScore(now.minusDays(365), now), 1e-9);
    }
}
