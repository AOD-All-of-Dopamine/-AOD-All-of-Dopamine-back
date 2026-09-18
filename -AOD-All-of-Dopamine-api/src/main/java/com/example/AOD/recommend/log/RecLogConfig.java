package com.example.AOD.recommend.log;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 로그 전용 연결 풀 (REC_TAB_DESIGN §5-8).
 * DataSource 빈을 직접 선언하면 Boot 기본 DataSource 가 생기지 않으므로 주 DataSource 도 함께 선언한다.
 */
@Configuration
public class RecLogConfig {

    /** 서비스용 주 풀. Boot 기본 구성과 같은 바인딩(spring.datasource.* + spring.datasource.hikari.*). */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    /** 로그 전용 풀 — 연결 2개. 서비스 풀(prod max 5)과 경쟁하지 않는다. */
    @Bean(name = "recLogDataSource")
    public HikariDataSource recLogDataSource(DataSourceProperties properties) {
        HikariDataSource ds = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        ds.setPoolName("rec-log");
        ds.setMaximumPoolSize(2);
        ds.setMinimumIdle(0);
        ds.setIdleTimeout(60_000);
        return ds;
    }

    @Bean(name = "recLogJdbcTemplate")
    public JdbcTemplate recLogJdbcTemplate(@Qualifier("recLogDataSource") DataSource recLogDataSource) {
        return new JdbcTemplate(recLogDataSource);
    }
}
