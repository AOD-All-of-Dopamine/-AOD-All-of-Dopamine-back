package com.example.AOD.recommend.log;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 로그 전용 연결 풀 (REC_TAB_DESIGN §5-8) — 연결 2개, 서비스 풀과 경쟁하지 않는다.
 *
 * 일부러 DataSource·JdbcTemplate·TransactionManager 를 **빈으로 노출하지 않는다**:
 *  - DataSource 빈을 선언하면 Boot 기본 DataSource 자동 구성이 물러난다
 *  - JdbcTemplate 빈을 선언하면 Boot 의 @Primary JdbcTemplate 이 사라져(@ConditionalOnMissingBean(JdbcOperations))
 *    기존 JdbcTemplate 주입 지점이 전부 이 2개짜리 풀에 붙는다
 *  - DataSource 빈이면 /actuator/health 의 db 집계에 들어가, 유실돼도 되는 로그 풀의 장애가 앱 헬스를 DOWN 으로 만든다
 *  - TransactionManager 빈이면 JPA 트랜잭션 매니저 자동 구성이 물러난다
 * 그래서 풀·템플릿·트랜잭션을 이 홀더 안에 가두고, 쓰는 쪽은 이 홀더를 주입받는다.
 */
@Component
public class RecLogJdbc implements DisposableBean {

    static final String POOL_NAME = "rec-log";
    static final int MAX_POOL_SIZE = 2;

    private final HikariDataSource pool;
    private final JdbcTemplate jdbc;
    private final TransactionOperations tx;

    public RecLogJdbc(DataSourceProperties properties, MeterRegistry meterRegistry) {
        HikariDataSource ds = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        ds.setPoolName(POOL_NAME);
        ds.setMaximumPoolSize(MAX_POOL_SIZE);
        ds.setMinimumIdle(0);
        ds.setIdleTimeout(60_000);
        ds.setConnectionTimeout(3_000);        // 로그 풀은 빨리 포기한다 — 종료 flush 예산(10초)을 지키기 위해서도
        ds.setMetricRegistry(meterRegistry);   // hikaricp_* 지표에 pool="rec-log" 로 잡힌다 (빈이 아니라 Boot 가 대신 묶어주지 않는다)
        this.pool = ds;
        this.jdbc = new JdbcTemplate(ds);
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    /** 로그 풀 위의 트랜잭션. 서비스(JPA) 트랜잭션과 무관하다. */
    public TransactionOperations tx() {
        return tx;
    }

    /** 테스트·점검용. */
    public HikariDataSource pool() {
        return pool;
    }

    @Override
    public void destroy() {
        pool.close();
    }
}
