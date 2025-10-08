package com.example.AOD.Novel.NaverSeriesNovel;

import com.example.AOD.ingest.CollectorService;
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
@Component
public class NaverSeriesCrawler {

    private final CollectorService collector;

    public NaverSeriesCrawler(CollectorService collector) {
        this.collector = collector;
    }

    public int crawlToRaw(String baseListUrl, String cookieString, int maxPages) throws Exception {
        int saved = 0;
        int page = 1;

        while (true) {
            if (maxPages > 0 && page > maxPages) break;

            String url = baseListUrl + page;
            Document listDoc = get(url, cookieString);

            Set<String> detailUrls = new LinkedHashSet<>();
            for (Element a : listDoc.select("a[href*='/novel/detail.series'][href*='productNo=']")) {
                String href = a.attr("href");
                if (!href.startsWith("http")) href = "https://series.naver.com" + href;
                detailUrls.add(href);
            }
            if (detailUrls.isEmpty()) {
                for (Element a : listDoc.select("a[href*='/novel/detail.series']")) {
                    String href = a.attr("href");
                    if (!href.startsWith("http")) href = "https://series.naver.com" + href;
                    detailUrls.add(href);
                }
            }

            if (detailUrls.isEmpty()) break;

            for (String detailUrl : detailUrls) {
                Document doc = get(detailUrl, cookieString);

                String productUrl = attr(doc.selectFirst("meta[property=og:url]"), "content");
                if (productUrl == null || productUrl.isBlank()) productUrl = detailUrl;

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
                        if ("연재중".equals(li.text()) || "완결".equals(li.text()) || "글".equals(label) || "출판사".equals(label) || "이용가".equals(label)) {
                            continue;
                        }
                        Element a = li.selectFirst("a");
                        if (a != null) {
                            String g = a.text().trim();
                            if (!g.isEmpty() && !genres.contains(g)) genres.add(g);
                        }
                    }
                }

                String synopsis = "";
                Elements synopsisElements = doc.select("div.end_dsc ._synopsis");
                if (!synopsisElements.isEmpty()) {
                    synopsis = text(synopsisElements.last()).replaceAll("\\s*접기$", "").trim();
                }

                String titleId = extractQueryParam(productUrl, "productNo");

                Map<String,Object> payload = new LinkedHashMap<>();
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

                collector.saveRaw("NaverSeries", "WEBNOVEL", payload, titleId, productUrl);
                saved++;
            }

            page++;
        }

        return saved;
    }

    /* ================= helpers ================ */

    private Document get(String url, String cookieString) throws Exception {
        var conn = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .referrer("https://series.naver.com/")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .timeout(15000);
        if (cookieString != null && !cookieString.isBlank()) {
            conn.header("Cookie", cookieString);
        }
        return conn.get();
    }

    private static String text(Element e) {
        return e == null ? "" : e.text().replace('\u00A0', ' ').trim();
    }
    private static String attr(Element e, String name) {
        return e == null ? null : e.attr(name);
    }

    private static String findInfoValue(Element infoUl, String label) {
        if (infoUl == null) return null;
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
        if (infoUl == null) return null;
        for (Element li : infoUl.select("> li")) {
            String t = text(li);
            if (t.contains("이용가")) return t;
        }
        return null;
    }

    private static BigDecimal extractRating(Document doc) {
        Element score = doc.selectFirst("div.score_area");
        if (score == null) return null;
        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(score.text());
        return m.find() ? new BigDecimal(m.group(1)) : null;
    }

    // ==================== [수정된 부분: 댓글 수 추출] ====================
    private static Long extractCommentCount(Document doc, Element head) {
        // 시도 1: 새로운 구조 <span id="commentCount">
        Element commentSpan = doc.selectFirst("span#commentCount");
        if (commentSpan != null) {
            Long n = parseKoreanCount(commentSpan.text());
            if (n != null) return n;
        }

        // 시도 2 (폴백): 기존 구조 h3:matchesOwn(댓글)
        Element h3 = doc.selectFirst("h3:matchesOwn(댓글)");
        if (h3 != null) {
            Element span = h3.selectFirst("span");
            if (span != null) {
                Long n = parseKoreanCount(span.text());
                if (n != null) return n;
            }
        }

        // 시도 3 (폴백): 헤더 텍스트
        if (head != null) {
            String t = head.text();
            Matcher m = Pattern.compile("관심\\s*(?:\\S+)\\s*(\\d+(?:\\.\\d+)?\\s*(?:만|천)|[\\d,]+)\\s*공유").matcher(t);
            if (m.find()) return parseKoreanCount(m.group(1));
        }
        return null;
    }
    // =======================================================================


    /** "2억 5,006만", "139.3만", "2.5천", "1,393,475" 등 지원 */
    private static Long parseKoreanCount(String s) {
        if (s == null) return null;
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
            } catch (NumberFormatException e) { /* 파싱 실패 시 다음 규칙으로 넘어감 */ }
        }

        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*만").matcher(s);
        if (m.find()) {
            return Math.round(Double.parseDouble(m.group(1)) * 10_000);
        }

        m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*천").matcher(s);
        if (m.find()) {
            return Math.round(Double.parseDouble(m.group(1)) * 1_000);
        }

        try { return Long.parseLong(s); } catch (Exception ignored) { return null; }
    }


    private static String extractQueryParam(String url, String key) {
        if (url == null) return null;
        int idx = url.indexOf('?');
        if (idx < 0) return null;
        String qs = url.substring(idx + 1);
        for (String p : qs.split("&")) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String cleanTitle(String raw) {
        if (raw == null) return null;
        return raw.replaceAll("\\s*\\[[^\\]]+\\]\\s*", " ").replaceAll("\\s+", " ").trim();
    }

    private static String nz(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}