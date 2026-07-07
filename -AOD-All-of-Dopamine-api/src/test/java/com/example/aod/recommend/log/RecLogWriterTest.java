package com.example.AOD.recommend.log;

import com.example.AOD.recommend.dto.RecommendationItem;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecLogWriterTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RecLogWriter writer = new RecLogWriter(jdbc);

    @Test
    void writesOneImpressionRowPerItem() {
        List<RecommendationItem> items = List.of(
                new RecommendationItem(10L, "MOVIE", 0.9, "quality", 0, "{}"),
                new RecommendationItem(11L, "GAME", 0.8, "quality", 1, "{}"));
        writer.writeImpressions(UUID.randomUUID(), 7L, "home", null, items);
        verify(jdbc, times(2)).update(
                contains("INSERT INTO aod_ai.rec_impression"),
                any(), any(), any(), any(), any(), any(), any(), any());
    }
}
