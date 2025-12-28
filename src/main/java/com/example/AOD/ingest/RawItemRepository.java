package com.example.AOD.ingest;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface RawItemRepository extends JpaRepository<RawItem, Long> {

    Optional<RawItem> findByHash(String hash);
    
    // platformName + platformSpecificId로 중복 검사 (같은 플랫폼의 같은 콘텐츠)
    Optional<RawItem> findByPlatformNameAndPlatformSpecificId(String platformName, String platformSpecificId);

    // Postgres: SKIP LOCKED로 다중 워커 경쟁 처리 가능
    @Query(value = """
      SELECT * FROM raw_items
      WHERE processed = false
      ORDER BY fetched_at
      LIMIT :batchSize
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
    List<RawItem> lockNextBatch(@Param("batchSize") int batchSize);


    long countByProcessedFalse();
}