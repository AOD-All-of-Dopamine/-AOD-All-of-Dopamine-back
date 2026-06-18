package com.example.crawler.contents.Novel.NaverSeriesNovel;

import com.example.crawler.ingest.CollectorService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Naver Series(웹소설) 크롤러
 * - 목록 페이지에서 상세 링크(productNo)만 수집
 * - 상세 페이지에서 필요한 필드만 추출
 * - 추출 결과를 평평한 Map payload로 raw_items에 저장
 */
@Slf4j
@Component
public class NaverSeriesCrawler {

    private final CollectorService collector;

    public NaverSeriesCrawler(CollectorService collector) {
        this.collector = collector;
    }

    /**
     * 신작 목록 크롤링 (recentList.series)
     * 
     * @param cookieString 쿠키 (선택)
     * @param maxPages     최대 페이지 수 (0이면 무제한)
     * @return 저장된 작품 수
     */
    public int crawlRecentNovels(String cookieString, int maxPages) throws Exception {
        String baseUrl = "https://series.naver.com/novel/recentList.series?page=";
        return crawlToRaw(baseUrl, cookieString, maxPages);
    }

    /**
     * 완결 작품 크롤링 (categoryProductList.series)
     * 
     * @param cookieString 쿠키 (선택)
     * @param maxPages     최대 페이지 수 (0이면 무제한)
     * @return 저장된 작품 수
     */
    public int crawlCompletedNovels(String cookieString, int maxPages) throws Exception {
        String baseUrl = "https://series.naver.com/novel/categoryProductList.series?categoryTypeCode=all&page=";
        return crawlToRaw(baseUrl, cookieString, maxPages);
    }

    /**
     * 단일 소설 크롤링 (Job Queue용)
     */
    public boolean collectNovelById(String productId) {
        try {
            log.debug("📖 [Novel] 소설 ID {} 크롤링 시작", productId);

            // 상세 페이지 URL 생성
            String detailUrl = "https://series.naver.com/novel/detail.series?productNo=" + productId;

            // cookieString 없이 크롤링 실행
            Document doc = get(detailUrl, null);

            // 19금 작품 체크
            Element adultMsg = doc.selectFirst("#adult_msg");
            Element enctp = doc.selectFirst("input[name=enctp]");
            boolean isAdultContent = (adultMsg != null) ||
                    (enctp != null && "19".equals(enctp.attr("value")));

            if (isAdultContent) {
                log.info("19금 작품으로 스킵: {}", detailUrl);
                return false;
            }

            // 작품 정보 추출
            String productUrl = attr(doc.selectFirst("meta[property=og:url]"), "content");
            if (productUrl == null || productUrl.isBlank())
                productUrl = detailUrl;

            String rawTitle = attr(doc.selectFirst("meta[property=og:title]"), "content");
            String title = cleanTitle(rawTitle != null ? rawTitle : text(doc.selectFirst("h2")));

            if (title == null || title.isBlank()) {
                log.warn("제목을 찾을 수 없는 작품 스킵: {}", detailUrl);
                return false;
            }

            String imageUrl = attr(doc.selectFirst("meta[property=og:image]"), "content");
            Element head = doc.selectFirst("div.end_head");
            BigDecimal rating = extractRating(doc);

            // 관심 수
            Long downloadCount = null;
            Element downloadBtnSpan = doc.selectFirst("a.btn_download > span");
            if (downloadBtnSpan != null) {
                downloadCount = parseKoreanCount(downloadBtnSpan.text());
            }

            // 댓글 수
            Long commentCount = extractCommentCount(doc, head);

            // 회차 수
            Long episodeCount = extractEpisodeCount(doc);

            // 작품 정보 (상태, 작가, 출판사, 이용가 등)
            Element infoUl = doc.selectFirst("ul.end_info li.info_lst > ul");
            String status = null;
            if (infoUl != null) {
                Element statusLi = infoUl.selectFirst("> li");
                if (statusLi != null) {
                    String statusText = statusLi.text().trim();
                    if ("연재중".equals(statusText) || "완결".equals(statusText)) {
                        status = statusText;
                    }
                }
            }

            String author = findInfoValue(infoUl, "글");
            String publisher = findInfoValue(infoUl, "출판사");
            String ageRating = findAge(infoUl);

            // 장르 추출
            List<String> genres = new ArrayList<>();
            if (infoUl != null) {
                for (Element li : infoUl.select("> li")) {
                    String label = text(li.selectFirst("> span"));
                    if ("연재중".equals(li.text()) || "완결".equals(li.text()) ||
                            "글".equals(label) || "출판사".equals(label) || "이용가".equals(label)) {
                        continue;
                    }
                    Element a = li.selectFirst("a");
                    if (a != null) {
                        String g = a.text().trim();
                        if (!g.isEmpty() && !genres.contains(g))
                            genres.add(g);
                    }
                }
            }

            // 줄거리
            String synopsis = "";
            Elements synopsisElements = doc.select("div.end_dsc ._synopsis");
            if (!synopsisElements.isEmpty()) {
                synopsis = text(synopsisElements.last()).replaceAll("\\s*접기$", "").trim();
            }

            String titleId = extractQueryParam(productUrl, "productNo");

            // 1화 날짜 추출
            String firstDate = null;
            if (titleId != null) {
                try {
                    firstDate = extractFirstEpisodeDate(titleId, null);
                } catch (Exception e) {
                    log.warn("1화 날짜 추출 실패 for {}: {}", titleId, e.getMessage());
                }
            }

            // payload 생성 및 저장
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("title", nz(title));
            payload.put("author", nz(author));
            payload.put("publisher", nz(publisher));
            payload.put("status", nz(status));
            payload.put("ageRating", nz(ageRating));
            payload.put("synopsis", nz(synopsis));
            payload.put("imageUrl", nz(imageUrl));
            payload.put("productUrl", nz(productUrl));
            payload.put("titleId", nz(titleId));
            payload.put("genres", genres);
            payload.put("rating", rating);
            payload.put("downloadCount", downloadCount);
            payload.put("commentCount", commentCount);
            payload.put("episodeCount", episodeCount);
            payload.put("firstDate", firstDate);

            collector.saveRaw("NaverSeries", "WEBNOVEL", payload, titleId, productUrl);

            log.debug("✅ [Novel] 소설 ID {} 크롤링 완료: {}", productId, title);
            return true;

        } catch (Exception e) {
            log.error("❌ [Novel] 소설 ID {} 크롤링 실패", productId, e);
            return false;
        }
    }

    public int crawlToRaw(String baseListUrl, String cookieString, int maxPages) throws Exception {
        int saved = 0;
        int page = 1;

        while (true) {
            // 인터럽트 체크 - 작업 취소 요청 확인
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("작업 인터럽트 감지, 크롤링 중단 (현재까지 " + saved + "개 저장)");
                return saved;
            }

            if (maxPages > 0 && page > maxPages)
                break;

            String url = baseListUrl + page;
            Document listDoc = get(url, cookieString);

            Set<String> detailUrls = new LinkedHashSet<>();
            for (Element a : listDoc.select("a[href*='/novel/detail.series'][href*='productNo=']")) {
                String href = a.attr("href");
                if (!href.startsWith("http"))
                    href = "https://series.naver.com" + href;
                detailUrls.add(href);
            }
            if (detailUrls.isEmpty()) {
                for (Element a : listDoc.select("a[href*='/novel/detail.series']")) {
                    String href = a.attr("href");
                    if (!href.startsWith("http"))
                        href = "https://series.naver.com" + href;
                    detailUrls.add(href);
                }
            }

            if (detailUrls.isEmpty())
                break;

            for (String detailUrl : detailUrls) {
                Document doc = get(detailUrl, cookieString);

                // 19금 작품 체크: adult_msg 또는 enctp="19" 존재 여부로 판단
                Element adultMsg = doc.selectFirst("#adult_msg");
                Element enctp = doc.selectFirst("input[name=enctp]");
                boolean isAdultContent = (adultMsg != null) ||
                        (enctp != null && "19".equals(enctp.attr("value")));

                if (isAdultContent) {
                    log.info("19금 작품으로 스킵: {}", detailUrl);
                    continue;
                }

                String productUrl = attr(doc.selectFirst("meta[property=og:url]"), "content");
                if (productUrl == null || productUrl.isBlank())
                    productUrl = detailUrl;

                String rawTitle = attr(doc.selectFirst("meta[property=og:title]"), "content");
                String title = cleanTitle(rawTitle != null ? rawTitle : text(doc.selectFirst("h2")));

                String imageUrl = attr(doc.selectFirst("meta[property=og:image]"), "content");
                Element head = doc.selectFirst("div.end_head");
                BigDecimal rating = extractRating(doc);

                // ⬇️ 다운로드(=관심) 수: 여러 위치에서 찾아보도록 로직 변경
                Long downloadCount = null;
                Element downloadBtnSpan = doc.selectFirst("a.btn_download > span"); // 1순위: user_action_area
                if (downloadBtnSpan != null) {
                    downloadCount = parseKoreanCount(downloadBtnSpan.text());
                }
                if (downloadCount == null && head != null) { // 2순위: end_head (폴백)
                    String headText = head.text();
                    Matcher m = Pattern.compile("관심\\s*([\\d.,]+\\s*(?:억|만|천)|[\\d,]+)").matcher(headText);
                    if (m.find()) {
                        downloadCount = parseKoreanCount(m.group(1));
                    }
                }

                // 💬 댓글 수: 여러 위치에서 찾아보도록 로직 변경
                Long commentCount = extractCommentCount(doc, head);

                // 📊 총 회차 수: "총 <strong>193</strong>화" 형식에서 추출
                Long episodeCount = extractEpisodeCount(doc);

                Element infoUl = doc.selectFirst("ul.end_info li.info_lst > ul");
                String status = null;
                if (infoUl != null) {
                    Element statusLi = infoUl.selectFirst("> li");
                    if (statusLi != null) {
                        String statusText = statusLi.text().trim();
                        if ("연재중".equals(statusText) || "완결".equals(statusText)) {
                            status = statusText;
                        }
                    }
                }

                String author = findInfoValue(infoUl, "글");
                String publisher = findInfoValue(infoUl, "출판사");
                String ageRating = findAge(infoUl);
                List<String> genres = new ArrayList<>();
                if (infoUl != null) {
                    for (Element li : infoUl.select("> li")) {
                        String label = text(li.selectFirst("> span"));
                        if ("연재중".equals(li.text()) || "완결".equals(li.text()) || "글".equals(label)
                                || "출판사".equals(label) || "이용가".equals(label)) {
                            continue;
                        }
                        Element a = li.selectFirst("a");
                        if (a != null) {
                            String g = a.text().trim();
                            if (!g.isEmpty() && !genres.contains(g))
                                genres.add(g);
                        }
                    }
                }

                String synopsis = "";
                Elements synopsisElements = doc.select("div.end_dsc ._synopsis");
                if (!synopsisElements.isEmpty()) {
                    synopsis = text(synopsisElements.last()).replaceAll("\\s*접기$", "").trim();
                }

                String titleId = extractQueryParam(productUrl, "productNo");

                // ========================================================
                // [추가됨] 2. 1화 날짜 추출을 위한 추가 요청 (volumeList.series)
                // ========================================================
                String firstDate = null;
                if (titleId != null) {
                    try {
                        // 헬퍼 메서드를 호출하여 1화 날짜를 가져옵니다.
                        firstDate = extractFirstEpisodeDate(titleId, cookieString);
                    } catch (Exception e) {
                        // 날짜 하나 못 가져왔다고 전체를 실패 처리할 필요는 없으므로 로그만 남김
                        System.err.println("Failed to extract first date for " + titleId + ": " + e.getMessage());
                    }
                }

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("title", nz(title));
                payload.put("author", nz(author));
                payload.put("publisher", nz(publisher));
                payload.put("status", nz(status));
                payload.put("ageRating", nz(ageRating));
                payload.put("synopsis", nz(synopsis));
                payload.put("imageUrl", nz(imageUrl));
                payload.put("productUrl", nz(productUrl));
                payload.put("titleId", nz(titleId));
                payload.put("genres", genres);

                payload.put("rating", rating);
                payload.put("downloadCount", downloadCount);
                payload.put("commentCount", commentCount);
                payload.put("episodeCount", episodeCount);

                // [추가됨] 1화 날짜 payload에 추가
                payload.put("firstDate", firstDate);

                collector.saveRaw("NaverSeries", "WEBNOVEL", payload, titleId, productUrl);
                saved++;
            }

            page++;
        }

        return saved;
    }

    /* ================= helpers ================ */

    // [추가됨] 1화 날짜 추출 로직
    protected String extractFirstEpisodeDate(String productNo, String cookieString) throws Exception {
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

    protected Document get(String url, String cookieString) throws Exception {
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

    private static String text(Element e) {
        return e == null ? "" : e.text().replace('\u00A0', ' ').trim();
    }

    private static String attr(Element e, String name) {
        return e == null ? null : e.attr(name);
    }

    private static String findInfoValue(Element infoUl, String label) {
        if (infoUl == null)
            return null;
        for (Element li : infoUl.select("> li")) {
            Element span = li.selectFirst("> span");
            if (span != null && label.equals(span.text().trim())) {
                Element a = li.selectFirst("a");
                return a != null ? a.text().trim() : li.ownText().trim();
            }
        }
        return null;
    }

    private static String findAge(Element infoUl) {
        if (infoUl == null)
            return null;
        for (Element li : infoUl.select("> li")) {
            String t = text(li);
            if (t.contains("이용가"))
                return t;
        }
        return null;
    }

    private static BigDecimal extractRating(Document doc) {
        Element score = doc.selectFirst("div.score_area");
        if (score == null)
            return null;
        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(score.text());
        return m.find() ? new BigDecimal(m.group(1)) : null;
    }

    // ==================== [수정된 부분: 댓글 수 추출] ====================
    private static Long extractCommentCount(Document doc, Element head) {
        // 시도 1: 새로운 구조 <span id="commentCount">
        Element commentSpan = doc.selectFirst("span#commentCount");
        if (commentSpan != null) {
            Long n = parseKoreanCount(commentSpan.text());
            if (n != null)
                return n;
        }

        // 시도 2 (폴백): 기존 구조 h3:matchesOwn(댓글)
        Element h3 = doc.selectFirst("h3:matchesOwn(댓글)");
        if (h3 != null) {
            Element span = h3.selectFirst("span");
            if (span != null) {
                Long n = parseKoreanCount(span.text());
                if (n != null)
                    return n;
            }
        }

        // 시도 3 (폴백): 헤더 텍스트
        if (head != null) {
            String t = head.text();
            Matcher m = Pattern.compile("관심\\s*(?:\\S+)\\s*(\\d+(?:\\.\\d+)?\\s*(?:만|천)|[\\d,]+)\\s*공유").matcher(t);
            if (m.find())
                return parseKoreanCount(m.group(1));
        }
        return null;
    }
    // =======================================================================

    /**
     * 총 회차 수 추출: "총 <strong>193</strong>화" 형식에서 숫자 추출
     * 
     * @param doc 상세 페이지 Document
     * @return 회차 수 (없으면 null)
     */
    private static Long extractEpisodeCount(Document doc) {
        Element episodeH5 = doc.selectFirst("h5.end_total_episode");
        if (episodeH5 != null) {
            Element strong = episodeH5.selectFirst("strong");
            if (strong != null) {
                try {
                    return Long.parseLong(strong.text().trim().replace(",", ""));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    /** "2억 5,006만", "139.3만", "2.5천", "1,393,475" 등 지원 */
    private static Long parseKoreanCount(String s) {
        if (s == null)
            return null;
        s = s.trim().replace(",", "");

        if (s.contains("억")) {
            String[] parts = s.split("억");
            long total = 0;
            try {
                total += Math.round(Double.parseDouble(parts[0].trim()) * 100_000_000);
                if (parts.length > 1 && !parts[1].isBlank()) {
                    String manPart = parts[1].replace("만", "").trim();
                    if (!manPart.isEmpty()) {
                        total += Math.round(Double.parseDouble(manPart) * 10_000);
                    }
                }
                return total;
            } catch (NumberFormatException e) {
                /* 파싱 실패 시 다음 규칙으로 넘어감 */ }
        }

        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*만").matcher(s);
        if (m.find()) {
            return Math.round(Double.parseDouble(m.group(1)) * 10_000);
        }

        m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*천").matcher(s);
        if (m.find()) {
            return Math.round(Double.parseDouble(m.group(1)) * 1_000);
        }

        try {
            return Long.parseLong(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * URL에서 쿼리 파라미터 추출 (공개 유틸리티 메서드)
     * 
     * @param url 전체 URL
     * @param key 추출할 파라미터 키
     * @return 파라미터 값 (없으면 null)
     */
    public static String extractQueryParam(String url, String key) {
        if (url == null)
            return null;
        int idx = url.indexOf('?');
        if (idx < 0)
            return null;
        String qs = url.substring(idx + 1);
        for (String p : qs.split("&")) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /**
     * 제목 정리: [독점], [시리즈 에디션] 등 태그 제거 (공개 유틸리티 메서드)
     * 
     * @param raw 원본 제목
     * @return 정리된 제목
     */
    public static String cleanTitle(String raw) {
        if (raw == null)
            return null;
        return raw.replaceAll("\\s*\\[[^\\]]+\\]\\s*", " ").replaceAll("\\s+", " ").trim();
    }

    private static String nz(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
