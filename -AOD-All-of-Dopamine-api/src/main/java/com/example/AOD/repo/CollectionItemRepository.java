package com.example.AOD.repo;

import com.example.AOD.domain.CollectionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CollectionItemRepository extends JpaRepository<CollectionItem, Long> {

    List<CollectionItem> findByCollectionIdOrderByPositionAscIdAsc(Long collectionId);

    boolean existsByCollectionIdAndContentContentId(Long collectionId, Long contentId);

    long countByCollectionId(Long collectionId);

    /** 말미 추가용 최대 position (아이템 없으면 null) */
    @Query("SELECT MAX(ci.position) FROM CollectionItem ci WHERE ci.collection.id = :collectionId")
    Integer findMaxPosition(@Param("collectionId") Long collectionId);

    /** 목록 카드용 아이템 수 배치 집계 — [collectionId, count] */
    @Query("SELECT ci.collection.id, COUNT(ci) FROM CollectionItem ci " +
           "WHERE ci.collection.id IN :collectionIds GROUP BY ci.collection.id")
    List<Object[]> countByCollectionIds(@Param("collectionIds") List<Long> collectionIds);

    /**
     * 목록 카드용 상위 20권 배치 조회 — [collectionId, contentId, masterTitle, posterUrl] (position 순).
     * 미니 책장의 책등(spines)과 커버 콜라주(coverPosters, 이 중 포스터가 있는 앞 3개)를 함께 파생한다.
     * 커버·책등은 저장하지 않고 담긴 작품에서 파생한다(플랜 결정).
     */
    @Query(value = "SELECT t.collection_id, t.content_id, t.master_title, t.poster_image_url FROM (" +
           "  SELECT ci.collection_id, c.content_id, c.master_title, c.poster_image_url, " +
           "         ROW_NUMBER() OVER (PARTITION BY ci.collection_id ORDER BY ci.position ASC, ci.id ASC) AS rn " +
           "  FROM collection_items ci JOIN contents c ON c.content_id = ci.content_id " +
           "  WHERE ci.collection_id IN (:collectionIds)" +
           ") t WHERE t.rn <= 20 ORDER BY t.collection_id, t.rn",
           nativeQuery = true)
    List<Object[]> findSpines(@Param("collectionIds") List<Long> collectionIds);

    /** 담기 팝오버용 — 주어진 컬렉션들 중 해당 작품을 이미 담고 있는 컬렉션 id */
    @Query("SELECT ci.collection.id FROM CollectionItem ci " +
           "WHERE ci.collection.id IN :collectionIds AND ci.content.contentId = :contentId")
    List<Long> findCollectionIdsContainingContent(@Param("collectionIds") List<Long> collectionIds,
                                                  @Param("contentId") Long contentId);

    @Modifying
    @Query("DELETE FROM CollectionItem ci WHERE ci.collection.id = :collectionId")
    int deleteAllByCollectionId(@Param("collectionId") Long collectionId);
}
