package com.example.AOD.ranking.steam.parser;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Steam HTML 파싱 전담 클래스 (SRP 준수)
 */
@Slf4j
@Component
public class SteamRankingParser {

    private static final Pattern APP_ID_PATTERN = Pattern.compile("/app/(\\d+)/");

    /**
     * Steam HTML Document에서 게임 랭킹 데이터 추출
     */
    public List<SteamGameData> parseRankings(Document doc) {
        if (doc == null) {
            log.warn("Document가 null입니다.");
            return new ArrayList<>();
        }

        Elements rows = doc.select("tr[class]");
        
        if (rows.isEmpty()) {
            log.error("랭킹 데이터 행을 찾을 수 없습니다. 페이지 구조가 변경되었을 수 있습니다.");
            return new ArrayList<>();
        }

        log.info("총 {}개의 랭킹 항목을 발견했습니다.", rows.size());
        
        List<SteamGameData> gameDataList = new ArrayList<>();
        
        for (Element row : rows) {
            try {
                SteamGameData gameData = parseRow(row);
                if (gameData != null) {
                    gameDataList.add(gameData);
                }
            } catch (Exception e) {
                log.warn("랭킹 항목 파싱 중 오류 발생 (건너뜀): {}", e.getMessage());
            }
        }

        return gameDataList;
    }

    /**
     * 개별 행 파싱
     */
    private SteamGameData parseRow(Element row) {
        // 순위 추출
        Integer rank = extractRank(row);
        if (rank == null) return null;

        // 제목 추출
        String title = extractTitle(row);
        if (title == null) return null;

        // AppID 추출
        Long appId = extractAppId(row);
        if (appId == null) return null;

        // 썸네일 URL 추출
        String thumbnailUrl = extractThumbnailUrl(row);

        return new SteamGameData(rank, title, appId, thumbnailUrl);
    }

    /**
     * 순위 추출 (두 번째 td 태그)
     */
    private Integer extractRank(Element row) {
        try {
            Element rankElement = row.select("td").get(1);
            if (rankElement != null) {
                return Integer.parseInt(rankElement.text().trim());
            }
        } catch (Exception e) {
            log.debug("순위 추출 실패: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 제목 추출 (_1n_4 패턴 또는 fallback)
     */
    private String extractTitle(Element row) {
        // 제목: '_1n_4' 패턴의 div (제목 전용 클래스)
        Element titleElement = row.selectFirst("div[class*='_1n_4']");
        
        if (titleElement == null) {
            // fallback: 숫자가 아닌 텍스트를 가진 첫 번째 div
            for (Element div : row.select("div")) {
                String text = div.ownText().trim();
                if (!text.isEmpty() && !text.matches("\\d+")) {
                    titleElement = div;
                    break;
                }
            }
        }

        return titleElement != null ? titleElement.text().trim() : null;
    }

    /**
     * AppID 추출 (URL에서 정규식으로 추출)
     */
    private Long extractAppId(Element row) {
        try {
            Element linkElement = row.selectFirst("a[href*='/app/']");
            if (linkElement == null) return null;

            String href = linkElement.attr("href");
            Matcher matcher = APP_ID_PATTERN.matcher(href);
            
            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            }
        } catch (Exception e) {
            log.debug("AppID 추출 실패: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 썸네일 이미지 URL 추출
     */
    private String extractThumbnailUrl(Element row) {
        try {
            // img 태그 찾기
            Element img = row.selectFirst("img");
            if (img != null) {
                String src = img.attr("src");
                if (src != null && !src.isEmpty()) {
                    return src;
                }
            }
        } catch (Exception e) {
            log.debug("썸네일 URL 추출 실패: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Steam 게임 데이터 DTO
     */
    public static class SteamGameData {
        private final Integer rank;
        private final String title;
        private final Long appId;
        private final String thumbnailUrl;

        public SteamGameData(Integer rank, String title, Long appId, String thumbnailUrl) {
            this.rank = rank;
            this.title = title;
            this.appId = appId;
            this.thumbnailUrl = thumbnailUrl;
        }

        public Integer getRank() {
            return rank;
        }

        public String getTitle() {
            return title;
        }

        public Long getAppId() {
            return appId;
        }

        public String getThumbnailUrl() {
            return thumbnailUrl;
        }
    }
}
