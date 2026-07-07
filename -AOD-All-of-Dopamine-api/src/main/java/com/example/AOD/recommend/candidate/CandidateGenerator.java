package com.example.AOD.recommend.candidate;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class CandidateGenerator {

    private static final int ANN_LIMIT = 300; // spec §5.1 소스별 ~300
    private final AiAssetRepository repo;

    public CandidateGenerator(AiAssetRepository repo) {
        this.repo = repo;
    }

    /** spec §5.1 union → dedupe. 비어있는 소스는 스킵(콜드스타트는 fallback만). */
    public List<Long> generate(String profileVector, String[] userTags,
                               String selectedVector, String[] selectedTags, int fallbackK) {
        Set<Long> pool = new LinkedHashSet<>();
        if (profileVector != null) pool.addAll(repo.findVectorCandidates(profileVector, ANN_LIMIT));
        if (userTags != null && userTags.length > 0) pool.addAll(repo.findFunTagCandidates(userTags));
        if (selectedVector != null) pool.addAll(repo.findVectorCandidates(selectedVector, ANN_LIMIT));
        if (selectedTags != null && selectedTags.length > 0) pool.addAll(repo.findFunTagCandidates(selectedTags));
        pool.addAll(repo.findQualityFallback(fallbackK));
        return new ArrayList<>(pool);
    }
}
