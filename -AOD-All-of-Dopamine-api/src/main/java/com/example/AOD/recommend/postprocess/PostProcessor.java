package com.example.AOD.recommend.postprocess;

import com.example.AOD.recommend.candidate.Candidate;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PostProcessor {

    private static final Set<String> ADULT_RATINGS = Set.of("19", "청소년이용불가", "ADULT");

    /** spec §5.4-1 hard filter: hidden / unavailable / 성인등급(비허용) 제외. */
    public List<Candidate> hardFilter(List<Candidate> candidates, boolean allowAdult) {
        List<Candidate> out = new ArrayList<>();
        for (Candidate c : candidates) {
            if (c.hidden || c.unavailable) continue;
            if (!allowAdult && c.ageRating != null && ADULT_RATINGS.contains(c.ageRating)) continue;
            out.add(c);
        }
        return out;
    }

    /** spec §5.4-2 soft: negative penalty ≤20%, recency boost ≤8% (score 기준 비율 상한). */
    public void applySoftAdjust(List<Candidate> candidates,
                                Map<Long, Double> negativePenalty,
                                Map<Long, Double> recencyBoost) {
        for (Candidate c : candidates) {
            double penalty = Math.min(negativePenalty.getOrDefault(c.contentId, 0.0), 0.20) * c.score;
            double boost = Math.min(recencyBoost.getOrDefault(c.contentId, 0.0), 0.08) * c.score;
            c.score = c.score - penalty + boost;
        }
    }

    /** spec §5.4-3 diversity: Top-N 내 한 domain ≤60%, 한 platform ≤50%(플랫폼 미상은 캡 면제, 초과분은 후순위 채움). */
    public List<Candidate> diversify(List<Candidate> ranked, int topN) {
        List<Candidate> pool = new ArrayList<>(ranked);
        pool.sort(Comparator.comparingDouble((Candidate c) -> c.score).reversed());
        int domainCap = (int) Math.floor(topN * 0.60);
        int platformCap = (int) Math.floor(topN * 0.50);
        Map<String, Integer> domainCount = new HashMap<>();
        Map<String, Integer> platformCount = new HashMap<>();
        List<Candidate> result = new ArrayList<>();
        List<Candidate> deferred = new ArrayList<>();
        for (Candidate c : pool) {
            if (result.size() >= topN) break;
            String plat = c.platforms.isEmpty() ? null : c.platforms.get(0);
            boolean platformOk = (plat == null) || platformCount.getOrDefault(plat, 0) < platformCap; // 미상 플랫폼 면제
            if (domainCount.getOrDefault(c.domain, 0) < domainCap && platformOk) {
                result.add(c);
                domainCount.merge(c.domain, 1, Integer::sum);
                if (plat != null) platformCount.merge(plat, 1, Integer::sum);
            } else {
                deferred.add(c);
            }
        }
        for (Candidate c : deferred) {
            if (result.size() >= topN) break;
            result.add(c);
        }
        return result;
    }
}
