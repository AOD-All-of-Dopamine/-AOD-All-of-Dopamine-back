package com.example.AOD.recommend;

import com.example.AOD.recommend.candidate.AiAssetRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AiAssetIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withUsername("postgres").withPassword("password")
            .withInitScript("db/it/aod_ai_it_setup.sql");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername); // = postgres (V4 grant 대상과 일치)
        r.add("spring.datasource.password", PG::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "none"); // 스키마는 init script가 제공
        r.add("spring.flyway.enabled", () -> "false");         // 아래에서 Flyway API로 직접 migrate
    }

    @Autowired AiAssetRepository repo;
    @Autowired DataSource dataSource;

    @Test
    void v4GrantAppliesAndNativeAodAiQueriesExecuteOnPgvector() {
        // (1) 실제 Flyway migrate — 비어있지 않은 스키마 위에서 baseline(3) 후 V4만 적용(Task 1 실증).
        Flyway.configure()
                .dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true).baselineVersion("3")
                .load().migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // (2) V4 grant 실효 — 서빙 계정(spring.datasource.username=postgres)이 실제 권한 보유(contracts §1.2).
        assertThat(jdbc.queryForObject(
                "SELECT has_table_privilege('postgres','aod_ai.rec_impression','INSERT')", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT has_table_privilege('postgres','aod_ai.content_embedding','SELECT')", Boolean.class)).isTrue();

        // (3) 네이티브 pgvector ANN 실행 — CAST(:vec AS vector) 바인딩 실제 동작(contracts §7).
        String vec = "[" + "0.1,".repeat(1023) + "0.1]";
        List<Long> ann = repo.findVectorCandidates(vec, 3);
        assertThat(ann).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(ann.get(0)).isEqualTo(1L); // embedding=0.1 콘텐츠가 최근접

        // (4) quality fallback ORDER BY 실행 + 하이드레이션 조인 실행(contracts §7).
        assertThat(repo.findQualityFallback(2)).containsExactly(1L, 3L); // 0.9, 0.7
        assertThat(repo.loadCandidateRows(List.of(1L, 2L, 3L))).hasSize(3);
    }
}
