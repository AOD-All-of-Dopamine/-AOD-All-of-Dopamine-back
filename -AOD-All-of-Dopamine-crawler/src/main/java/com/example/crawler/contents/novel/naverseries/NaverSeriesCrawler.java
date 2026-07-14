package com.example.crawler.contents.novel.naverseries;

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
     * 단일 소설 크롤링 (Job Queue용)
     */
    public boolean collectNovelById(String productId) {
        try {
            log.debug("📖 [Novel] 소설 ID {} 크롤링 시작", productId);
            String detailUrl = "https://series.naver.com/novel/detail.series?productNo=" + productId;
            boolean saved = crawlDetailToRaw(detailUrl, null);
            if (saved) {
                log.debug("✅ [Novel] 소설 ID {} 크롤링 완료", productId);
            }
            return saved;
        } catch (Exception e) {
            log.error("❌ [Novel] 소설 ID {} 크롤링 실패", productId, e);
            return false;
        }
    }

    /**
     * 목록 페이지네이션을 따라가며 상세를 수집 (어드민 수동 트리거용)
     */
    public int crawlToRaw(String baseListUrl, String cookieString, int maxPages) throws Exception {
        int saved = 0;
        int page = 1;

        while (true) {
            // 인터럽트 체크 - 작업 취소 요청 확인
            if (Thread.currentThread().isInterrupted()) {
                log.info("작업 인터럽트 감지, 크롤링 중단 (현재까지 {}개 저장)", saved);
                return saved;
            }

            if (maxPages > 0 && page > maxPages)
                break;

            Document listDoc = get(baseListUrl + page, cookieString);
            Set<String> detailUrls = extractDetailUrls(listDoc);
            if (detailUrls.isEmpty())
                break;

            for (String detailUrl : detailUrls) {
                try {
                    if (crawlDetailToRaw(detailUrl, cookieString)) {
                        saved++;
                    }
                } catch (Exception e) {
                    log.warn("상세 크롤링 실패, 스킵: {}, {}", detailUrl, e.getMessage());
                }
            }

            page++;
        }

        return saved;
    }

    /**
     * 상세 페이지 1건 파싱 → raw_items 저장.
     * 19금 작품·제목 없는 작품은 스킵하고 false 반환.
     * (Job Queue 단건 경로와 목록 경로가 공유하는 단일 파싱 지점)
     */
    private boolean crawlDetailToRaw(String detailUrl, String cookieString) throws Exception {
        Document doc = get(detailUrl, cookieString);

        // 19금 작품 체크: adult_msg 또는 enctp="19" 존재 여부로 판단
        Element adultMsg = doc.selectFirst("#adult_msg");
        Element enctp = doc.selectFirst("input[name=enctp]");
        if (adultMsg != null || (enctp != null && "19".equals(enctp.attr("value")))) {
            log.info("19금 작품으로 스킵: {}", detailUrl);
            return false;
        }

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

        // 관심 수 (다운로드 버튼 수치 → end_head "관심 N" 텍스트 폴백)
        Long downloadCount = null;
        Element downloadBtnSpan = doc.selectFirst("a.btn_download > span");
        if (downloadBtnSpan != null) {
            downloadCount = parseKoreanCount(downloadBtnSpan.text());
        }
        if (downloadCount == null && head != null) {
            Matcher m = Pattern.compile("관심\\s*([\\d.,]+\\s*(?:억|만|천)|[\\d,]+)").matcher(head.text());
            if (m.find()) {
                downloadCount = parseKoreanCount(m.group(1));
            }
        }

        Long commentCount = extractCommentCount(doc, head);
        Long episodeCount = extractEpisodeCount(doc);

        Element infoUl = doc.selectFirst("ul.end_info li.info_lst > ul");
        String status = extractStatus(infoUl);
        String author = findInfoValue(infoUl, "글");
        String publisher = findInfoValue(infoUl, "출판사");
        String ageRating = findAge(infoUl);
        List<String> genres = extractGenres(infoUl);

        String synopsis = "";
        Elements synopsisElements = doc.select("div.end_dsc ._synopsis");
        if (!synopsisElements.isEmpty()) {
            synopsis = text(synopsisElements.last()).replaceAll("\\s*접기$", "").trim();
        }

        String titleId = extractQueryParam(productUrl, "productNo");

        // 1화 날짜 추출 (별도 volumeList API)
        String firstDate = null;
        if (titleId != null) {
            try {
                firstDate = extractFirstEpisodeDate(titleId, cookieString);
            } catch (Exception e) {
                log.warn("1화 날짜 추출 실패 for {}: {}", titleId, e.getMessage());
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
        payload.put("firstDate", firstDate);

        collector.saveRaw("NaverSeries", "WEBNOVEL", payload, titleId, productUrl);
        return true;
    }

    /* ================= helpers ================ */

    /** 목록 페이지에서 상세 링크 수집 (productNo 링크 우선, 없으면 전체 detail 링크 폴백) */
    private static Set<String> extractDetailUrls(Document listDoc) {
        Set<String> detailUrls = new LinkedHashSet<>();
        for (Element a : listDoc.select("a[href*='/novel/detail.series'][href*='productNo=']")) {
            detailUrls.add(absolutize(a.attr("href")));
        }
        if (detailUrls.isEmpty()) {
            for (Element a : listDoc.select("a[href*='/novel/detail.series']")) {
                detailUrls.add(absolutize(a.attr("href")));
            }
        }
        return detailUrls;
    }

    private static String absolutize(String href) {
        return href.startsWith("http") ? href : "https://series.naver.com" + href;
    }

    /** 작품정보란 첫 항목이 연재중/완결일 때만 상태로 인정 */
    private static String extractStatus(Element infoUl) {
        if (infoUl == null)
            return null;
        Element statusLi = infoUl.selectFirst("> li");
        if (statusLi == null)
            return null;
        String statusText = statusLi.text().trim();
        return ("연재중".equals(statusText) || "완결".equals(statusText)) ? statusText : null;
    }

    /** 작품정보란에서 상태/글/출판사/이용가를 제외한 링크들을 장르로 수집 */
    private static List<String> extractGenres(Element infoUl) {
        List<String> genres = new ArrayList<>();
        if (infoUl == null)
            return genres;
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
        return genres;
    }

    /**
     * 1화 날짜 추출: volumeList를 sortOrder=ASC로 요청하면 첫 항목이 1화이고,
     * lastVolumeUpdateDate는 회차별 등록(업데이트) 시각이라 첫 등장 값 = 1화 등록일.
     * (2026-07 실측 검증: 1화=2018-03-23, 최신화=2025-08-08로 회차별 값 확인)
     */
    private String extractFirstEpisodeDate(String productNo, String cookieString) throws Exception {
        String apiUrl = "https://series.naver.com/novel/volumeList.series?productNo=" + productNo
                + "&sortOrder=ASC&page=1";

        var conn = Jsoup.connect(apiUrl)
                .userAgent(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .referrer("https://series.naver.com/")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .ignoreContentType(true)
                .timeout(10000)
                .maxBodySize(2 * 1024 * 1024);

        if (cookieString != null && !cookieString.isBlank()) {
            conn.header("Cookie", cookieString);
        }

        String jsonResponse = conn.execute().body();

        // "lastVolumeUpdateDate":"2018-03-23 00:01:59" 형태에서 첫 등장 값 추출
        int idx = jsonResponse.indexOf("\"lastVolumeUpdateDate\"");
        if (idx >= 0) {
            int startQuote = jsonResponse.indexOf("\"", idx + 23);
            if (startQuote >= 0) {
                int endQuote = jsonResponse.indexOf("\"", startQuote + 1);
                if (endQuote >= 0) {
                    String dateTime = jsonResponse.substring(startQuote + 1, endQuote);
                    // "2018-03-23 00:01:59" -> "2018-03-23" (LocalDate.parse() 호환)
                    if (dateTime.length() >= 10) {
                        return dateTime.substring(0, 10);
                    }
                }
            }
        }

        log.debug("1화 날짜를 찾지 못함 productNo={}", productNo);
        return null;
    }

    private Document get(String url, String cookieString) throws Exception {
        var conn = Jsoup.connect(url)
                .userAgent(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .referrer("https://series.naver.com/")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .timeout(10000)
                .maxBodySize(5 * 1024 * 1024)
                .ignoreHttpErrors(false);
        if (cookieString != null && !cookieString.isBlank()) {
            conn.header("Cookie", cookieString);
        }
        return conn.get();
    }

    private static String text(Element e) {
        return e == null ? "" : e.text().replace(' ', ' ').trim();
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

    /**
     * 총 회차 수 추출: "총 <strong>193</strong>화" 형식에서 숫자 추출
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
