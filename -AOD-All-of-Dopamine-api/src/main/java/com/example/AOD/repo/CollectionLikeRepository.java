package com.example.AOD.repo;

import com.example.AOD.domain.CollectionLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CollectionLikeRepository extends JpaRepository<CollectionLike, Long> {

    boolean existsByCollectionIdAndUserId(Long collectionId, Long userId);

    /** 목록 카드 likedByMe 배치 조회 — 로그인 사용자가 좋아요한 컬렉션 id */
    @Query("SELECT cl.collection.id FROM CollectionLike cl " +
           "WHERE cl.user.id = :userId AND cl.collection.id IN :collectionIds")
    List<Long> findLikedCollectionIds(@Param("userId") Long userId,
                                      @Param("collectionIds") List<Long> collectionIds);

    /**
     * 멱등 좋아요 삽입 — (collection_id, user_id) 유니크 제약 기반 ON CONFLICT DO NOTHING.
     * 반환값 1이면 신규 삽입(카운트 +1 대상), 0이면 이미 좋아요 상태(no-op).
     * 동시 요청에도 유니크 제약이 중복 행을 막아 경합 안전.
     */
    @Modifying
    @Query(value = "INSERT INTO collection_likes (collection_id, user_id, created_at) " +
           "VALUES (:collectionId, :userId, NOW()) " +
           "ON CONFLICT (collection_id, user_id) DO NOTHING",
           nativeQuery = true)
    int insertIfAbsent(@Param("collectionId") Long collectionId, @Param("userId") Long userId);

    /** 멱등 좋아요 취소 — 반환값 1이면 실제 삭제(카운트 -1 대상), 0이면 이미 취소 상태 */
    @Modifying
    @Query("DELETE FROM CollectionLike cl WHERE cl.collection.id = :collectionId AND cl.user.id = :userId")
    int deleteByCollectionIdAndUserId(@Param("collectionId") Long collectionId, @Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM CollectionLike cl WHERE cl.collection.id = :collectionId")
    int deleteAllByCollectionId(@Param("collectionId") Long collectionId);
}
