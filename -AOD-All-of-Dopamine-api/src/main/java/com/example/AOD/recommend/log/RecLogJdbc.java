package com.example.AOD.recommend.log;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.jdbc.DataSourceBuilder;
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

    public RecLogJdbc(DataSourceProperties properties,
                      ObjectProvider<JdbcConnectionDetails> connectionDetails,
                      MeterRegistry meterRegistry) {
        // 주 DataSource 와 같은 접속 정보를 쓴다. Boot 3.1+ 의 JdbcConnectionDetails(@ServiceConnection 등)가 있으면 그쪽이 우선이다.
        JdbcConnectionDetails details = connectionDetails.getIfAvailable();
        DataSourceBuilder<?> builder = details != null
                ? DataSourceBuilder.create()
                        .url(details.getJdbcUrl())
                        .username(details.getUsername())
                        .password(details.getPassword())
                        .driverClassName(details.getDriverClassName())
                : properties.initializeDataSourceBuilder();
        HikariDataSource ds = builder.type(HikariDataSource.class).build();
        ds.setPoolName(POOL_NAME);
        ds.setMaximumPoolSize(MAX_POOL_SIZE);
        ds.setMinimumIdle(0);
        ds.setIdleTimeout(60_000);
        ds.setConnectionTimeout(3_000);        // 로그 풀은 빨리 포기한다 — 종료 flush 예산(10초)을 지키기 위해서도
        // 파티션 생성은 부모 테이블에 ACCESS EXCLUSIVE 잠금을 건다. 로그 풀의 어떤 문장도 잠금을 5초 넘게 기다리지 않게 한다
        // (기다리다 실패한 배치는 버려진다 — 로그는 유실을 허용한다. 끝없이 멈추는 것보다 낫다).
        ds.setConnectionInitSql("SET lock_timeout = '5s'");
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
