package com.example.AOD.recommend.candidate;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import java.lang.reflect.Method;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AiAssetRepositoryTest {

    private Query queryOf(String name, Class<?>... params) throws Exception {
        Method m = AiAssetRepository.class.getMethod(name, params);
        Query q = m.getAnnotation(Query.class);
        assertNotNull(q, name + " must have @Query");
        assertTrue(q.nativeQuery(), name + " must be nativeQuery");
        return q;
    }

    @Test
    void vectorCandidateQueryMatchesContract() throws Exception {
        assertEquals("SELECT content_id FROM aod_ai.content_embedding "
                + "ORDER BY embedding <=> CAST(:vec AS vector) LIMIT :k",
                queryOf("findVectorCandidates", String.class, int.class).value());
    }

    @Test
    void funTagCandidateQueryMatchesContract() throws Exception {
        assertEquals("SELECT content_id FROM aod_ai.content_fun_tag WHERE tag = ANY(:tags)",
                queryOf("findFunTagCandidates", String[].class).value());
    }

    @Test
    void qualityFallbackQueryOrdersByPopularity() throws Exception {
        assertEquals("SELECT content_id FROM aod_ai.content_quality_score "
                + "ORDER BY quality_popularity_score DESC NULLS LAST LIMIT :k",
                queryOf("findQualityFallback", int.class).value());
    }

    @Test
    void positiveCountQueryReadsUserProfileCache() throws Exception {
        assertEquals("SELECT positive_count FROM aod_ai.user_profile_cache WHERE user_id = :userId",
                queryOf("findPositiveCount", long.class).value());
    }

    @Test
    void loadCandidateRowsJoinsContentsAndQuality() throws Exception {
        assertEquals("SELECT c.content_id, c.domain, c.release_date, q.quality_popularity_score "
                + "FROM public.contents c "
                + "LEFT JOIN aod_ai.content_quality_score q ON q.content_id = c.content_id "
                + "WHERE c.content_id IN (:ids)",
                queryOf("loadCandidateRows", List.class).value());
    }
}
