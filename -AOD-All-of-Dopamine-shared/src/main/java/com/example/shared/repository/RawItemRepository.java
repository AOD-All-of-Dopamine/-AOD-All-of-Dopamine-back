package com.example.shared.repository;

import com.example.shared.entity.RawItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RawItemRepository extends JpaRepository<RawItem, Long> {
    
    Optional<RawItem> findByHash(String hash);
    
    // platformName + platformSpecificId로 중복 검사
    Optional<RawItem> findByPlatformNameAndPlatformSpecificId(String platformName, String platformSpecificId);
    
    // Postgres: SKIP LOCKED로 다중 워커 경쟁 처리
    @Query(value = """
      SELECT * FROM raw_items
      WHERE processed = false
      ORDER BY fetched_at
      LIMIT :batchSize
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
    List<RawItem> lockNextBatch(@Param("batchSize") int batchSize);
    
    long countByProcessedFalse();
    
    @Query("SELECT r FROM RawItem r WHERE r.platformName = :platformName AND r.processed = false ORDER BY r.fetchedAt ASC")
    List<RawItem> findPendingItemsByPlatform(@Param("platformName") String platformName, @Param("limit") int limit);
}
