package com.example.AOD.recommend.feature;

import com.example.AOD.recommend.dto.FunTag;
import com.example.AOD.recommend.dto.QualityScore;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class FeatureCalculator {

    /** spec §5.2 fun_tag overlap: Σ(공유 태그) profileWeight × (tag_score × tag_confidence). */
    public double funTagMatchScore(Map<String, Double> profileTagWeights, List<FunTag> candidateTags) {
        if (profileTagWeights == null || profileTagWeights.isEmpty() || candidateTags == null) return 0.0;
        double sum = 0.0;
        for (FunTag t : candidateTags) {
            Double w = profileTagWeights.get(t.tag());
            if (w != null) sum += w * (t.tagScore() * t.tagConfidence());
        }
        return sum;
    }

    /** spec §5.2 profile cosine similarity. */
    public double profileSimilarityScore(float[] profileVector, float[] candidateVector) {
        if (profileVector == null || candidateVector == null
                || profileVector.length == 0 || profileVector.length != candidateVector.length) return 0.0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < profileVector.length; i++) {
            dot += profileVector[i] * candidateVector[i];
            na += (double) profileVector[i] * profileVector[i];
            nb += (double) candidateVector[i] * candidateVector[i];
        }
        if (na == 0 || nb == 0) return 0.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** spec §5.2 metadata match. M2 단순화: 장르 Jaccard만 (creator/platform/domain은 M3). */
    public double metadataMatchScore(Set<String> profileGenres, Set<String> candidateGenres) {
        if (profileGenres == null || candidateGenres == null
                || profileGenres.isEmpty() || candidateGenres.isEmpty()) return 0.0;
        long inter = candidateGenres.stream().filter(profileGenres::contains).count();
        long union = profileGenres.size() + candidateGenres.size() - inter;
        return union == 0 ? 0.0 : (double) inter / union;
    }

    /** spec §5.2 quality lookup: content_quality_score.quality_popularity_score. */
    public double qualityPopularityScore(QualityScore quality) {
        return quality == null ? 0.0 : quality.qualityPopularityScore();
    }

    /** spec §5.2 recency: 발매일 1.0, 365일 선형 감쇠 후 0. */
    public double recencyScore(LocalDate releaseDate, LocalDate now) {
        if (releaseDate == null || now == null) return 0.0;
        long days = ChronoUnit.DAYS.between(releaseDate, now);
        if (days < 0) days = 0;
        if (days >= 365) return 0.0;
        return 1.0 - (days / 365.0);
    }
}
