package com.example.AOD.recommend.candidate;

import com.example.shared.entity.Content;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.util.List;

/** aod_ai 스키마 네이티브 조회. contracts §7 SQL 그대로. vec는 '[0.1,0.2,...]' 문자열. */
public interface AiAssetRepository extends Repository<Content, Long> {

    @Query(value = "SELECT content_id FROM aod_ai.content_embedding "
            + "ORDER BY embedding <=> CAST(:vec AS vector) LIMIT :k", nativeQuery = true)
    List<Long> findVectorCandidates(@Param("vec") String vec, @Param("k") int k);

    @Query(value = "SELECT content_id FROM aod_ai.content_fun_tag WHERE tag = ANY(:tags)",
            nativeQuery = true)
    List<Long> findFunTagCandidates(@Param("tags") String[] tags);

    @Query(value = "SELECT content_id FROM aod_ai.content_quality_score "
            + "ORDER BY quality_popularity_score DESC NULLS LAST LIMIT :k", nativeQuery = true)
    List<Long> findQualityFallback(@Param("k") int k);

    /** M3 콜드스타트 분기(§7 positive_count)용 — 계약 고정, M2 서빙 경로에서는 미호출. */
    @Query(value = "SELECT positive_count FROM aod_ai.user_profile_cache WHERE user_id = :userId",
            nativeQuery = true)
    Integer findPositiveCount(@Param("userId") long userId);

    /** 후보 집합 하이드레이션: domain·release_date(public.contents) + quality(aod_ai) 로드. */
    @Query(value = "SELECT c.content_id, c.domain, c.release_date, q.quality_popularity_score "
            + "FROM public.contents c "
            + "LEFT JOIN aod_ai.content_quality_score q ON q.content_id = c.content_id "
            + "WHERE c.content_id IN (:ids)", nativeQuery = true)
    List<Object[]> loadCandidateRows(@Param("ids") List<Long> ids);
}
