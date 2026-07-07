package com.example.AOD.recommend.rank;

import com.example.AOD.recommend.feature.FeatureVector;
import org.springframework.stereotype.Component;

@Component
public class Ranker {

    /** contracts §7: home = 0.45·funtag + 0.25·profile + 0.15·quality + 0.10·metadata + 0.05·recency. */
    public double homeScore(FeatureVector f) {
        return 0.45 * f.funTag()
             + 0.25 * f.profileSim()
             + 0.15 * f.quality()
             + 0.10 * f.metadata()
             + 0.05 * f.recency();
    }
    // related_score(0.4·home + 0.6·selected_sim)는 related 서빙과 함께 M3에서 추가.
}
