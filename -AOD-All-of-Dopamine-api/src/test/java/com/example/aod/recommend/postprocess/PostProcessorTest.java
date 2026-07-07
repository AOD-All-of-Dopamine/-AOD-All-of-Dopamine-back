package com.example.AOD.recommend.postprocess;

import com.example.AOD.recommend.candidate.Candidate;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PostProcessorTest {

    private final PostProcessor pp = new PostProcessor();

    private Candidate c(long id, String domain, double score) {
        Candidate x = new Candidate(id, domain);
        x.score = score;
        return x;
    }

    @Test
    void hardFilterRemovesHiddenUnavailableAndAdultWhenNotAllowed() {
        Candidate hidden = c(1, "MOVIE", 1); hidden.hidden = true;
        Candidate gone = c(2, "MOVIE", 1); gone.unavailable = true;
        Candidate adult = c(3, "MOVIE", 1); adult.ageRating = "청소년이용불가";
        Candidate ok = c(4, "MOVIE", 1);
        List<Candidate> out = pp.hardFilter(List.of(hidden, gone, adult, ok), false);
        assertEquals(1, out.size());
        assertEquals(4L, out.get(0).contentId);
    }

    @Test
    void hardFilterKeepsAdultWhenAllowed() {
        Candidate adult = c(3, "MOVIE", 1); adult.ageRating = "19";
        assertEquals(1, pp.hardFilter(List.of(adult), true).size());
    }

    @Test
    void softAdjustCapsPenaltyAt20AndBoostAt8Percent() {
        Candidate a = c(1, "MOVIE", 1.0);
        pp.applySoftAdjust(List.of(a), Map.of(1L, 0.50), Map.of(1L, 0.50));
        // penalty min(0.5,0.2)*1.0=0.2, boost min(0.5,0.08)*1.0=0.08 → 1.0-0.2+0.08
        assertEquals(0.88, a.score, 1e-9);
    }

    @Test
    void diversityPromotesLowerDomainAboveExcessSameDomain() {
        // topN=3 → domainCap=floor(1.8)=1 → MOVIE 1개만 1차 통과.
        // 후보 모두 platforms 비어있음 → platform 캡 면제(가짜 "" 버킷 캡 방지) → GAME(c2) 승격.
        // 1차: [5](MOVIE), c4·c3 domain 초과 defer, [5,2](GAME 통과) → defer=[4,3] 채움 → [5,2,4].
        List<Candidate> ranked = List.of(
                c(5, "MOVIE", 5), c(4, "MOVIE", 4), c(3, "MOVIE", 3), c(2, "GAME", 2));
        List<Candidate> out = pp.diversify(ranked, 3);
        assertEquals(3, out.size());
        assertEquals(List.of(5L, 2L, 4L),
                out.stream().map(x -> x.contentId).toList());
    }
}
