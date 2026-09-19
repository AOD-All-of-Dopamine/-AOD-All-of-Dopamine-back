package com.example.AOD.recommend.migration;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V9 파일 계약. 실제 실행은 RecommendIntegrationTest 가 검증한다. */
class V9MigrationContractTest {

    private String read(String name) throws Exception {
        URL url = getClass().getResource("/db/migration/" + name);
        assertNotNull(url, name + " 이 있어야 한다");
        return Files.readString(Paths.get(url.toURI()), StandardCharsets.UTF_8);
    }

    @Test
    void addsSkippedKeysColumnIdempotently() throws Exception {
        String sql = read("V9__rec_chain_skipped_keys.sql");
        assertTrue(sql.contains("ALTER TABLE aod_rec.rec_chain"), "대상 테이블이 aod_rec.rec_chain 이어야 한다");
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS skipped_keys text[]"), "멱등하게 추가해야 한다");
        assertTrue(sql.contains("NOT NULL DEFAULT '{}'"), "기존 행이 있으므로 기본값이 있어야 한다");
    }

    @Test
    void v8IsNotTouched() throws Exception {
        // V8 은 로컬·운영에 이미 적용됐다 — 고치면 Flyway 체크섬이 어긋난다.
        assertFalse(read("V8__create_rec_schemas.sql").contains("skipped_keys"),
                "skipped_keys 는 V9 에서만 추가한다");
    }
}
