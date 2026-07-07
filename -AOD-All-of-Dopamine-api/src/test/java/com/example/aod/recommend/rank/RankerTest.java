package com.example.AOD.recommend.rank;

import com.example.AOD.recommend.feature.FeatureVector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RankerTest {

    private final Ranker ranker = new Ranker();

    @Test
    void homeScoreUsesContractWeights() {
        // 0.45+0.25+0.15+0.10+0.05 = 1.0
        assertEquals(1.0, ranker.homeScore(new FeatureVector(1, 1, 1, 1, 1)), 1e-9);
        // 0.45*0.2 + 0.25*0.4 + 0.15*0.6 + 0.10*0.8 + 0.05*1.0 = 0.41
        assertEquals(0.41, ranker.homeScore(new FeatureVector(0.2, 0.4, 0.6, 0.8, 1.0)), 1e-9);
    }
}
