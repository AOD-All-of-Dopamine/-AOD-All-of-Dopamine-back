package com.example.AOD.config;

import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway 기준선 설정 (2026-08 배선).
 *
 * 프로퍼티가 아닌 코드로 두는 이유: prod 이미지는 gitignore된 application.properties가
 * 없어 환경변수로만 설정되는데, 기준선 누락 시 기존 스키마에서 기동이 실패한다.
 * 코드에 두면 모든 프로파일에서 동일하게 적용된다.
 *
 * baseline-version=3: 기존 DB(로컬·prod)는 V1(GIN 인덱스)·V3(도메인 platforms 컬럼)의
 * 효과가 이미 반영돼 있으므로 V4(컬렉션 테이블)부터 실행한다.
 * 빈 DB(신규 환경)는 기준선 없이 V1부터 전부 실행된다 (전 마이그레이션 멱등).
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayConfigurationCustomizer aodFlywayCustomizer() {
        return configuration -> configuration
                .baselineOnMigrate(true)
                .baselineVersion("3");
    }
}
