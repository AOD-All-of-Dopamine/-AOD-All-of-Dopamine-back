package com.example.crawler.contents.Novel.NaverSeriesNovel;

import com.example.crawler.crawl.CrawlPayload;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure parser: NaverSeries novel detail Document → CrawlPayload. Returns null to SKIP (adult / no title). */
@Slf4j
@Component
public class NaverSeriesNovelParser {

    /**
     * @param productId the productNo (used only for logging context)
     * @param detail    the fetched detail page + first-episode date
     * @return CrawlPayload, or null if the novel should be skipped (adult content or missing title)
     */
    public CrawlPayload parse(String productId, NaverSeriesDetail detail) {
        Document doc = detail.doc();

        // 19금 작품 체크
        Element adultMsg = doc.selectFirst("#adult_msg");
        Element enctp = doc.selectFirst("input[name=enctp]");
        boolean isAdultContent = (adultMsg != null) || (enctp != null && "19".equals(enctp.attr("value")));
        if (isAdultContent) {
            log.info("19금 작품으로 스킵: productNo={}", productId);
            return null;
        }

        String productUrl = attr(doc.selectFirst("meta[property=og:url]"), "content");
        if (productUrl == null || productUrl.isBlank()) {
            productUrl = "https://series.naver.com/novel/detail.series?productNo=" + productId;
        }

        String rawTitle = attr(doc.selectFirst("meta[property=og:title]"), "content");
        String title = cleanTitle(rawTitle != null ? rawTitle : text(doc.selectFirst("h2")));
        if (title == null || title.isBlank()) {
            log.warn("제목을 찾을 수 없는 작품 스킵: productNo={}", productId);
            return null;
        }

        String imageUrl = attr(doc.selectFirst("meta[property=og:image]"), "content");
        Element head = doc.selectFirst("div.end_head");
        BigDecimal rating = extractRating(doc);

        // 다운로드(=관심) 수: 1순위 a.btn_download>span, 2순위 div.end_head 텍스트 폴백.
        // 폴백은 기존 crawlToRaw(관리자 일괄 경로)에만 있던 로직으로, 두 경로를 통합하며 파서에 추가함.
        Long downloadCount = null;
        Element downloadBtnSpan = doc.selectFirst("a.btn_download > span");
        if (downloadBtnSpan != null) {
            downloadCount = parseKoreanCount(downloadBtnSpan.text());
        }
        if (downloadCount == null && head != null) {
            String headText = head.text();
            Matcher m = Pattern.compile("관심\\s*([\\d.,]+\\s*(?:억|만|천)|[\\d,]+)").matcher(headText);
            if (m.find()) {
                downloadCount = parseKoreanCount(m.group(1));
            }
        }

        Long commentCount = extractCommentCount(doc, head);
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
                if ("연재중".equals(li.text()) || "완결".equals(li.text()) ||
                        "글".equals(label) || "출판사".equals(label) || "이용가".equals(label)) {
                    continue;
                }
                Element a = li.selectFirst("a");
                if (a != null) {
                    String g = a.text().trim();
                    if (!g.isEmpty() && !genres.contains(g)) {
                        genres.add(g);
                    }
                }
            }
        }

        String synopsis = "";
        Elements synopsisElements = doc.select("div.end_dsc ._synopsis");
        if (!synopsisElements.isEmpty()) {
            synopsis = text(synopsisElements.last()).replaceAll("\\s*접기$", "").trim();
        }

        String titleId = extractQueryParam(productUrl, "productNo");

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
        payload.put("firstDate", detail.firstDate());

        return new CrawlPayload(titleId, productUrl, payload);
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

    // --- public static helpers (also used by the ranking module) ---

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
