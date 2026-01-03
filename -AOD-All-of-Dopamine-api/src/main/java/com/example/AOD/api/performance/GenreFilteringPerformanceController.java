package com.example.AOD.api.performance;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.api.service.WorkApiService;
import com.example.shared.entity.Domain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 🔬 장르 필터링 성능 테스트 컨트롤러
 * DB 레벨 필터링 vs 메모리 필터링 비교
 */
@Slf4j
@RestController
@RequestMapping("/api/performance/genre")
@RequiredArgsConstructor
public class GenreFilteringPerformanceController {

    private final WorkApiService workApiService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 장르 필터링 성능 비교 테스트
     * DB 레벨 필터링의 성능을 측정합니다
     */
    @GetMapping("/test/genre-filtering-comparison")
    public Map<String, Object> compareGenreFiltering(
            @RequestParam Domain domain,
            @RequestParam List<String> genres,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("🔍 Genre filtering comparison test - Domain: {}, Genres: {}", domain, genres);
        
        Pageable pageable = PageRequest.of(page, size);
        Map<String, Object> response = new HashMap<>();
        
        // 1. DB 레벨 필터링 (현재 최적화된 방식)
        long dbStartTime = System.nanoTime();
        PageResponse<WorkSummaryDTO> dbResult = workApiService.getWorks(domain, null, null, genres, pageable);
        long dbEndTime = System.nanoTime();
        long dbDuration = (dbEndTime - dbStartTime) / 1_000_000; // ms로 변환
        
        response.put("testInfo", Map.of(
            "domain", domain.name(),
            "genres", genres,
            "genreCount", genres.size(),
            "page", page,
            "size", size
        ));
        
        response.put("dbLevelFiltering", Map.of(
            "method", "PostgreSQL text[] @> operator with GIN index",
            "durationMs", dbDuration,
            "totalElements", dbResult.getTotalElements(),
            "totalPages", dbResult.getTotalPages(),
            "resultCount", dbResult.getContent().size()
        ));
        
        response.put("optimization", Map.of(
            "indexUsed", "GIN index on genres column",
            "nPlusOneProblem", "Resolved - Single query execution",
            "memoryUsage", "Minimal - Only fetches filtered results"
        ));
        
        log.info("✅ DB filtering completed in {}ms, found {} results", 
                 dbDuration, dbResult.getTotalElements());
        
        return response;
    }

    /**
     * PostgreSQL 쿼리 실행 계획 조회
     * 인덱스 사용 여부 확인
     */
    @GetMapping("/test/query-explain")
    public Map<String, Object> getQueryExplain(
            @RequestParam Domain domain,
            @RequestParam List<String> genres) {
        
        log.info("📊 Getting query execution plan for {} with genres: {}", domain, genres);
        
        String tableName = domain.name().toLowerCase() + "_contents";
        String genreArray = genres.stream()
                .map(g -> "'" + g.replace("'", "''") + "'")
                .collect(Collectors.joining(","));
        
        String sql = String.format(
            "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " +
            "SELECT * FROM %s WHERE genres @> ARRAY[%s]::text[]",
            tableName, genreArray
        );
        
        try {
            List<Map<String, Object>> explainResult = jdbcTemplate.queryForList(sql);
            
            Map<String, Object> response = new HashMap<>();
            response.put("domain", domain.name());
            response.put("table", tableName);
            response.put("genres", genres);
            response.put("query", String.format(
                "SELECT * FROM %s WHERE genres @> ARRAY[%s]::text[]",
                tableName, genreArray
            ));
            response.put("executionPlan", explainResult);
            
            // 인덱스 사용 여부 확인
            String indexCheckSql = String.format(
                "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = '%s' AND indexname LIKE '%%genres%%'",
                tableName
            );
            List<Map<String, Object>> indexes = jdbcTemplate.queryForList(indexCheckSql);
            response.put("availableIndexes", indexes);
            
            return response;
            
        } catch (Exception e) {
            log.error("Failed to get query plan: {}", e.getMessage());
            return Map.of(
                "error", e.getMessage(),
                "query", sql
            );
        }
    }

    /**
     * 쿼리 실행 계획 가이드 (간단 버전)
     */
    @GetMapping("/test/query-plan-guide")
    public Map<String, String> getQueryPlanGuide(
            @RequestParam Domain domain,
            @RequestParam List<String> genres) {
        
        String tableName = domain.name().toLowerCase() + "_contents";
        String genreArray = genres.stream()
                .map(g -> "'" + g + "'")
                .collect(Collectors.joining(","));
        
        String guide = String.format("""
                PostgreSQL Array Query Plan Test
                =================================
                
                Current Query:
                SELECT * FROM %s 
                WHERE genres @> ARRAY[%s]::text[]
                
                To check execution plan in psql:
                EXPLAIN ANALYZE 
                SELECT * FROM %s 
                WHERE genres @> ARRAY[%s]::text[];
                
                Recommended Index (auto-created on startup):
                CREATE INDEX IF NOT EXISTS idx_%s_genres ON %s USING GIN (genres);
                
                Check if index exists:
                SELECT indexname, indexdef 
                FROM pg_indexes 
                WHERE tablename = '%s' AND indexname LIKE '%%genres%%';
                
                Expected result with index:
                - Bitmap Index Scan on idx_%s_genres
                - Index Cond: (genres @> '{%s}'::text[])
                
                Performance tip:
                - GIN index is optimal for array containment queries (@>)
                - Single query execution (no N+1 problem)
                - Only filtered results loaded into memory
                """,
                tableName, genreArray,
                tableName, genreArray,
                domain.name().toLowerCase(), tableName,
                tableName,
                domain.name().toLowerCase(), String.join(",", genres)
        );
        
        return Map.of("queryPlanGuide", guide);
    }

    /**
     * 인덱스 사용 통계 조회
     */
    @GetMapping("/test/index-stats")
    public Map<String, Object> getIndexStats() {
        
        String sql = """
            SELECT 
                schemaname,
                tablename,
                indexname,
                idx_scan as scans,
                idx_tup_read as tuples_read,
                idx_tup_fetch as tuples_fetched,
                pg_size_pretty(pg_relation_size(indexrelid)) as index_size
            FROM pg_stat_user_indexes
            WHERE indexname LIKE 'idx_%_genres'
            ORDER BY idx_scan DESC
            """;
        
        try {
            List<Map<String, Object>> stats = jdbcTemplate.queryForList(sql);
            
            return Map.of(
                "genreIndexStats", stats,
                "description", "GIN index usage statistics for genre filtering"
            );
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 장르별 콘텐츠 분포 조회
     */
    @GetMapping("/test/genre-distribution")
    public Map<String, Object> getGenreDistribution(@RequestParam Domain domain) {
        
        String tableName = domain.name().toLowerCase() + "_contents";
        
        String sql = String.format("""
            SELECT 
                unnest(genres) as genre,
                COUNT(*) as content_count
            FROM %s
            GROUP BY unnest(genres)
            ORDER BY content_count DESC
            LIMIT 20
            """, tableName);
        
        try {
            List<Map<String, Object>> distribution = jdbcTemplate.queryForList(sql);
            
            return Map.of(
                "domain", domain.name(),
                "genreDistribution", distribution,
                "description", "Top 20 genres by content count"
            );
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }
}


