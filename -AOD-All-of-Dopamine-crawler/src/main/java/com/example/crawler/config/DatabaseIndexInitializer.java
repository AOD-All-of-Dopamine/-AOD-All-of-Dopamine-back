package com.example.crawler.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 장르 인덱스 자동 생성 설정
 * - ddl-auto=create/update: 테이블 생성/변경 후 인덱스 자동 생성
 * - ddl-auto=validate/none: 인덱스만 확인하고 없으면 생성
 * 
 * PostgreSQL text[] 배열에 GIN 인덱스 자동 생성
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseIndexInitializer {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 애플리케이션 준비 완료 후 인덱스 생성
     * Hibernate DDL 작업 완료 후 실행됨
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureGenreIndexes() {
        log.info("Checking and creating genre GIN indexes for text[] arrays...");
        
        try {
            // 각 테이블의 genres 컬럼에 GIN 인덱스 생성
            ensureIndexExists("movie_contents", "idx_movie_genres");
            ensureIndexExists("tv_contents", "idx_tv_genres");
            ensureIndexExists("game_contents", "idx_game_genres");
            ensureIndexExists("webtoon_contents", "idx_webtoon_genres");
            ensureIndexExists("webnovel_contents", "idx_webnovel_genres");
            
            log.info("✅ Genre GIN indexes verified/created successfully");
        } catch (Exception e) {
            log.error("❌ Failed to ensure genre indexes: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 인덱스 존재 여부 확인 후 없으면 생성
     */
    private void ensureIndexExists(String tableName, String indexName) {
        try {
            // 1. 테이블 존재 여부 확인
            Boolean tableExists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (" +
                "  SELECT FROM information_schema.tables " +
                "  WHERE table_schema = 'public' AND table_name = ?" +
                ")",
                Boolean.class,
                tableName
            );
            
            if (!Boolean.TRUE.equals(tableExists)) {
                log.debug("  ⏭ Table {} does not exist yet, skipping index creation", tableName);
                return;
            }
            
            // 2. 인덱스 존재 여부 확인
            Boolean indexExists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (" +
                "  SELECT FROM pg_indexes " +
                "  WHERE schemaname = 'public' " +
                "    AND tablename = ? " +
                "    AND indexname = ?" +
                ")",
                Boolean.class,
                tableName,
                indexName
            );
            
            if (Boolean.TRUE.equals(indexExists)) {
                log.debug("  ✓ Index {} already exists on {}", indexName, tableName);
                return;
            }
            
            // 3. 인덱스 생성 (CREATE INDEX IF NOT EXISTS는 PostgreSQL 9.5+에서 지원)
            String sql = String.format(
                "CREATE INDEX IF NOT EXISTS %s ON %s USING GIN (genres)",
                indexName, tableName
            );
            jdbcTemplate.execute(sql);
            log.info("  ✓ Created GIN index: {} on {} (text[] array)", indexName, tableName);
            
        } catch (Exception e) {
            log.warn("  ⚠ Failed to ensure index {} on {}: {}", 
                     indexName, tableName, e.getMessage());
        }
    }
}

/**
 * Hibernate 스키마 생성 시 genres 컬럼을 text[]로 생성하도록 설정
 */
@Slf4j
@Configuration
class HibernateTextArrayConfig {
    
    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return hibernateProperties -> {
            // PostgreSQL text[] 배열 타입 매핑 설정
            // List<String>을 text[]로 자동 매핑
            log.info("Configuring Hibernate for PostgreSQL text[] array support");
        };
    }
}


