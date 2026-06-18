package com.example.crawler.contents.Novel.NaverSeriesNovel;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/** Jsoup IO for NaverSeries novels: fetch the detail page and the first-episode date. */
@Slf4j
@Component
public class NaverSeriesNovelFetcher {

    private static final String DETAIL_URL = "https://series.naver.com/novel/detail.series?productNo=";

    /** Fetch a novel detail (detail page + first-episode date). cookieString may be null. */
    public NaverSeriesDetail fetchDetail(String productId, String cookieString) throws Exception {
        Document doc = get(DETAIL_URL + productId, cookieString);
        String firstDate = null;
        try {
            firstDate = extractFirstEpisodeDate(productId, cookieString);
        } catch (Exception e) {
            log.warn("1화 날짜 추출 실패 for {}: {}", productId, e.getMessage());
        }
        return new NaverSeriesDetail(doc, firstDate);
    }

    /** Convenience for the queue path (no cookie), matching the legacy collectNovelById behavior. */
    public NaverSeriesDetail fetchDetail(String productId) throws Exception {
        return fetchDetail(productId, null);
    }

    /** Fetch a detail Document for an already-resolved URL (used by the admin list crawl). */
    public Document getByUrl(String detailUrl, String cookieString) throws Exception {
        return get(detailUrl, cookieString);
    }

    // --- moved VERBATIM from NaverSeriesCrawler (body unchanged) ---

    // [추가됨] 1화 날짜 추출 로직
    private String extractFirstEpisodeDate(String productNo, String cookieString) throws Exception {
        // sortOrder=ASC 파라미터를 사용하여 1화부터 정렬된 리스트를 요청
        String apiUrl = "https://series.naver.com/novel/volumeList.series?productNo=" + productNo
                + "&sortOrder=ASC&page=1";
        System.out.println("[DEBUG] Fetching first episode date for productNo=" + productNo);

        // JSON 응답을 받음
        var conn = Jsoup.connect(apiUrl)
                .userAgent(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .referrer("https://series.naver.com/")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .ignoreContentType(true)
                .timeout(10000)           // 🚀 15초 → 10초
                .maxBodySize(2 * 1024 * 1024);  // 🚀 2MB JSON 제한

        if (cookieString != null && !cookieString.isBlank()) {
            conn.header("Cookie", cookieString);
        }

        // JSON 응답을 텍스트로 받아서 파싱
        String jsonResponse = null;
        try {
            jsonResponse = conn.execute().body();
            System.out.println("[DEBUG] JSON response length: " + jsonResponse.length() + " chars");

            // JSON에서 lastVolumeUpdateDate 추출 (간단한 문자열 파싱)
            // 형식: "lastVolumeUpdateDate":"2025-08-20 00:01:38"
            int idx = jsonResponse.indexOf("\"lastVolumeUpdateDate\"");
            System.out.println("[DEBUG] lastVolumeUpdateDate field found at index: " + idx);

            if (idx >= 0) {
                int startQuote = jsonResponse.indexOf("\"", idx + 23);
                if (startQuote >= 0) {
                    int endQuote = jsonResponse.indexOf("\"", startQuote + 1);
                    if (endQuote >= 0) {
                        String dateTime = jsonResponse.substring(startQuote + 1, endQuote);
                        System.out.println("[DEBUG] Extracted dateTime: " + dateTime);

                        // "2025-08-20 00:01:38" -> "2025-08-20" (ISO 8601 형식 유지, LocalDate.parse() 호환)
                        if (dateTime != null && dateTime.length() >= 10) {
                            String formattedDate = dateTime.substring(0, 10); // yyyy-MM-dd 형식 유지
                            System.out.println("[DEBUG] Formatted date: " + formattedDate);
                            return formattedDate;
                        }
                    }
                }
            }

            System.out.println("[DEBUG] Failed to extract date for productNo=" + productNo);
            return null;
        } finally {
            // 🚀 연결 리소스 해제
            conn = null;
        }
    }

    private Document get(String url, String cookieString) throws Exception {
        var conn = Jsoup.connect(url)
                .userAgent(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .referrer("https://series.naver.com/")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .timeout(10000)           // 🚀 15초 → 10초 (빠른 실패)
                .maxBodySize(5 * 1024 * 1024)  // 🚀 5MB 제한 (메모리 보호)
                .ignoreHttpErrors(false);  // 🚀 HTTP 오류 시 즉시 실패
        if (cookieString != null && !cookieString.isBlank()) {
            conn.header("Cookie", cookieString);
        }

        try {
            return conn.get();
        } finally {
            // 연결 명시적 종료 (리소스 해제)
            conn = null;
        }
    }
}
