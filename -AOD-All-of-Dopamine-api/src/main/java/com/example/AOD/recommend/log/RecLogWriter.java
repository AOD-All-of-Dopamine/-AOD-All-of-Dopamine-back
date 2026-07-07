package com.example.AOD.recommend.log;

import com.example.AOD.recommend.dto.RecommendationItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;

@Component
public class RecLogWriter {

    private static final String INSERT_SQL =
            "INSERT INTO aod_ai.rec_impression "
          + "(request_id, user_id, location, selected_content_id, content_id, "
          + "candidate_source, rank_position, score_breakdown) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))";

    private final JdbcTemplate jdbc;

    public RecLogWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** contracts §7: 노출 아이템마다 rec_impression 1행 insert. */
    public void writeImpressions(UUID requestId, Long userId, String location,
                                 Long selectedContentId, List<RecommendationItem> items) {
        for (RecommendationItem it : items) {
            jdbc.update(INSERT_SQL,
                    requestId, userId, location, selectedContentId, it.contentId(),
                    it.candidateSource(), it.rankPosition(), it.scoreBreakdownJson());
        }
    }
}
