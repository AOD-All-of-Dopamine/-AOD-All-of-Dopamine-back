package com.example.crawler.ranking.steam.parser;

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
    private static final int MAX_ITEMS = 100;

    /**
     * Steam HTML Document에서 게임 랭킹 데이터 추출
     */
    public List<SteamGameData> parseRankings(Document doc) {
        if (doc == null) {
            log.warn("Document가 null입니다.");
            return new ArrayList<>();
        }

        // 여러 선택자 시도 (Steam 페이지 구조 변경 대응)
        Elements rows = doc.select("tr[data-ds-appid]");
        
        if (rows.isEmpty()) {
            rows = doc.select("tr[class]");
        }
        
        if (rows.isEmpty()) {
            rows = doc.select("[data-ds-appid]");
        }

        if (rows.isEmpty()) {
            log.error("랭킹 데이터 행을 찾을 수 없습니다. 페이지 구조가 변경되었을 수 있습니다.");
            log.debug("페이지 HTML 샘플: {}", doc.html().substring(0, Math.min(2000, doc.html().length())));
            return new ArrayList<>();
        }

        log.info("총 {}개의 랭킹 항목을 발견했습니다.", rows.size());

        List<SteamGameData> gameDataList = new ArrayList<>();
        int rank = 1;

        for (Element row : rows) {
            if (rank > MAX_ITEMS) break;
            
            try {
                SteamGameData gameData = parseRow(row, rank);
                if (gameData != null) {
                    gameDataList.add(gameData);
                    rank++;
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
    private SteamGameData parseRow(Element row, int currentRank) {
        // AppID 추출 (data-ds-appid 속성 우선, 없으면 URL에서 추출)
        Long appId = extractAppIdFromAttribute(row);
        if (appId == null) {
            appId = extractAppIdFromUrl(row);
        }
        if (appId == null) return null;

        // 순위 추출 (실패시 currentRank 사용)
        Integer rank = extractRank(row);
        if (rank == null) {
            rank = currentRank;
        }

        // 제목 추출
        String title = extractTitle(row);
        if (title == null || title.isEmpty()) {
            title = "Steam App " + appId;
        }

        // 썸네일 URL 추출 (없으면 Steam 표준 URL 생성)
        String thumbnailUrl = extractThumbnailUrl(row);
        if (thumbnailUrl == null || thumbnailUrl.isEmpty()) {
            thumbnailUrl = "https://cdn.akamai.steamstatic.com/steam/apps/" + appId + "/header.jpg";
        }

        return new SteamGameData(rank, title, appId, thumbnailUrl);
    }

    /**
     * data-ds-appid 속성에서 AppID 추출
     */
    private Long extractAppIdFromAttribute(Element row) {
        try {
            String appIdStr = row.attr("data-ds-appid");
            if (appIdStr != null && !appIdStr.isEmpty()) {
                return Long.parseLong(appIdStr);
            }
        } catch (Exception e) {
            log.debug("data-ds-appid 속성 추출 실패: {}", e.getMessage());
        }
        return null;
    }

    /**
     * AppID 추출 (URL에서 정규식으로 추출)
     */
    private Long extractAppIdFromUrl(Element row) {
        try {
            Element linkElement = row.selectFirst("a[href*='/app/']");
            if (linkElement == null) return null;

            String href = linkElement.attr("href");
            Matcher matcher = APP_ID_PATTERN.matcher(href);

            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            }
        } catch (Exception e) {
            log.debug("AppID URL 추출 실패: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 순위 추출 (두 번째 td 태그)
     */
    private Integer extractRank(Element row) {
        try {
            Elements tds = row.select("td");
            if (tds.size() > 1) {
                Element rankElement = tds.get(1);
                if (rankElement != null) {
                    String text = rankElement.text().trim();
                    if (text.matches("\\d+")) {
                        return Integer.parseInt(text);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("순위 추출 실패: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 제목 추출 (여러 선택자 시도)
     */
    private String extractTitle(Element row) {
        // 1. GameName 클래스 패턴
        Element titleElement = row.selectFirst("[class*='GameName']");
        
        // 2. _1n_4 패턴 (이전 버전)
        if (titleElement == null) {
            titleElement = row.selectFirst("div[class*='_1n_4']");
        }
        
        // 3. 링크 텍스트
        if (titleElement == null) {
            titleElement = row.selectFirst("a[href*='/app/']");
        }

        // 4. fallback: 숫자가 아닌 텍스트를 가진 첫 번째 div
        if (titleElement == null) {
            for (Element div : row.select("div")) {
                String text = div.ownText().trim();
                if (!text.isEmpty() && !text.matches("\\d+") && text.length() > 2) {
                    titleElement = div;
                    break;
                }
            }
        }

        return titleElement != null ? titleElement.text().trim() : null;
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

        @Override
        public String toString() {
            return "SteamGameData{" +
                    "rank=" + rank +
                    ", title='" + title + '\'' +
                    ", appId=" + appId +
                    '}';
        }
    }
}


