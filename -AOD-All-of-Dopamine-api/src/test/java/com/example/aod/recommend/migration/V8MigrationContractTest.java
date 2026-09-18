package com.example.AOD.recommend.migration;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V8 파일 계약. 실제 실행은 RecLogIntegrationTest(Testcontainers)가 검증한다. */
class V8MigrationContractTest {

    private String sql() throws Exception {
        URL url = getClass().getResource("/db/migration/V8__create_rec_schemas.sql");
        assertNotNull(url, "V8__create_rec_schemas.sql 이 있어야 한다 (V7 다음 순번)");
        return Files.readString(Paths.get(url.toURI()), StandardCharsets.UTF_8);
    }

    @Test
    void createsBothSchemasAndAllTables() throws Exception {
        String sql = sql();
        assertTrue(sql.contains("CREATE SCHEMA IF NOT EXISTS aod_rec"));
        assertTrue(sql.contains("CREATE SCHEMA IF NOT EXISTS aod_log"));
        for (String t : new String[]{"aod_rec.rec_chain", "aod_rec.not_interested", "aod_rec.corpus_map",
                "aod_log.rec_request", "aod_log.rec_item_served", "aod_log.event", "aod_log.client_agent",
                "aod_log.event_seen", "aod_log.rejected_event"}) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + t), t + " 누락");
        }
    }

    @Test
    void everyPartitionedTableHasDefaultPartition() throws Exception {
        String sql = sql();
        for (String t : new String[]{"rec_request", "rec_item_served", "event", "client_agent"}) {
            assertTrue(sql.contains("aod_log." + t + "_default"), t + " DEFAULT 파티션 누락 (§5-3)");
        }
    }

    @Test
    void partitionBoundsArePinnedToUtc() throws Exception {
        assertTrue(sql().contains("T00:00:00Z"), "월 경계는 UTC 리터럴이어야 한다");
    }

    @Test
    void monthPartitionNamingMatchesJavaJobConvention() throws Exception {
        String sql = sql();
        // {table}_yYYYYmMM — PartitionMaintenanceJob(PartitionNames)이 같은 규칙으로 이후 달을 만든다
        assertTrue(sql.contains("'_y' || to_char(m, 'YYYY') || 'm' || to_char(m, 'MM')"),
                "월 파티션 이름 규칙이 바뀌면 자바 작업과 어긋난다");
        assertTrue(sql.contains("AT TIME ZONE 'UTC'"), "이번 달 계산은 UTC 기준이어야 한다");
    }
}
