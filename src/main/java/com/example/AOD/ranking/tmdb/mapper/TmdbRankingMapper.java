package com.example.AOD.ranking.tmdb.mapper;

import com.example.AOD.ranking.entity.ExternalRanking;
import com.example.AOD.ranking.tmdb.constant.TmdbPlatformType;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * TMDB JSON 데이터를 ExternalRanking 엔티티로 변환 (SRP 준수)
 */
@Slf4j
@Component
public class TmdbRankingMapper {

    public List<ExternalRanking> mapToRankings(JsonNode jsonNode, TmdbPlatformType platformType) {
        if (jsonNode == null || !jsonNode.has("results")) {
            log.warn("유효하지 않은 TMDB 응답 데이터입니다.");
            return new ArrayList<>();
        }

        List<ExternalRanking> rankings = new ArrayList<>();
        int rank = 1;

        for (JsonNode item : jsonNode.get("results")) {
            try {
                ExternalRanking ranking = createRanking(item, rank++, platformType);
                rankings.add(ranking);
            } catch (Exception e) {
                log.warn("TMDB 랭킹 항목 변환 중 오류 발생 (건너뜀): {}", e.getMessage());
            }
        }

        return rankings;
    }

    private ExternalRanking createRanking(JsonNode item, int rank, TmdbPlatformType platformType) {
        // 필수 필드 검증
        if (!item.has("id") || !item.has(platformType.getTitleField())) {
            throw new IllegalArgumentException("필수 필드가 누락되었습니다: id 또는 " + platformType.getTitleField());
        }

        ExternalRanking ranking = new ExternalRanking();
        ranking.setPlatformSpecificId(String.valueOf(item.get("id").asLong()));
        ranking.setTitle(item.get(platformType.getTitleField()).asText());
        ranking.setRanking(rank);
        ranking.setPlatform(platformType.getPlatformName());
        
        // 썸네일 URL 추출 (poster_path)
        if (item.has("poster_path") && !item.get("poster_path").isNull()) {
            String posterPath = item.get("poster_path").asText();
            String thumbnailUrl = "https://image.tmdb.org/t/p/w500" + posterPath;
            ranking.setThumbnailUrl(thumbnailUrl);
        }

        return ranking;
    }
}
