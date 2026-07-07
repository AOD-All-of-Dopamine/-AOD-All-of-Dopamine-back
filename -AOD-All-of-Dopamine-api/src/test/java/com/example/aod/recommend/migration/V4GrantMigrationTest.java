package com.example.AOD.recommend.migration;

import org.junit.jupiter.api.Test;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.*;

class V4GrantMigrationTest {

    @Test
    void v4GrantFileExistsAndGrantsToServingDatasourceUser() throws Exception {
        URL url = getClass().getResource("/db/migration/V4__grant_aod_ai_access.sql");
        assertNotNull(url, "V4__grant_aod_ai_access.sql must exist (V3 다음 순번)");
        String sql = Files.readString(Paths.get(url.toURI()), StandardCharsets.UTF_8);
        // grant 대상 = spring.datasource.username(=postgres). 계약에 없는 롤 이름 금지.
        assertTrue(sql.contains("GRANT USAGE ON SCHEMA aod_ai TO postgres"));
        assertTrue(sql.contains("GRANT SELECT ON aod_ai.content_embedding TO postgres"));
        assertTrue(sql.contains("GRANT SELECT ON aod_ai.content_fun_tag TO postgres"));
        assertTrue(sql.contains("GRANT SELECT ON aod_ai.content_quality_score TO postgres"));
        assertTrue(sql.contains("GRANT INSERT ON aod_ai.rec_impression TO postgres"));
        assertTrue(sql.contains("GRANT INSERT ON aod_ai.rec_event TO postgres"));
        assertFalse(sql.contains("aod_api"), "존재하지 않는 롤에 grant 금지(서빙 계정과 불일치)");
    }
}
