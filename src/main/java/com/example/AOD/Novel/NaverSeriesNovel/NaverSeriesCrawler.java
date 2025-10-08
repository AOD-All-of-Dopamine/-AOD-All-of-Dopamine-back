package com.example.AOD.Novel.NaverSeriesNovel;

import com.example.AOD.ingest.CollectorService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements; // Elements 임포트 추가
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

    /**
     * 목록 페이지 순회 → 상세 파싱 → raw_items 저장
     * @param baseListUrl e.g. https://series.naver.com/novel/top100List.series?rankingTypeCode=DAILY&categoryCode=ALL&page=
     * @param cookieString 필요 시 쿠키(로그인/성인)
     * @param maxPages 0 또는 음수면 결과 없을 때까지
     * @return 저장 건수
     */
    public int crawlToRaw(String baseListUrl, String cookieString, int maxPages) throws Exception {
        int saved = 0;
        int page = 1;

        while (true) {
            if (maxPages > 0 && page > maxPages) break;

            String url = baseListUrl + page;
            Document listDoc = get(url, cookieString);

            // 상세 링크 직접 수집 (li 구조 무시)
            Set<String> detailUrls = new LinkedHashSet<>();
            for (Element a : listDoc.select("a[href*='/novel/detail.series'][href*='productNo=']")) {
                String href = a.attr("href");
                if (!href.startsWith("http")) href = "https://series.naver.com" + href;
                detailUrls.add(href);
            }
            // 폴백
            if (detailUrls.isEmpty()) {
                for (Element a : listDoc.select("a[href*='/novel/detail.series']")) {
                    String href = a.attr("href");
                    if (!href.startsWith("http")) href = "https://series.naver.com" + href;
                    detailUrls.add(href);
                }
            }

            if (detailUrls.isEmpty()) break;

            for (String detailUrl : detailUrls) {
                // ===== 상세 파싱 =====
                Document doc = get(detailUrl, cookieString);

                String productUrl = attr(doc.selectFirst("meta[property=og:url]"), "content");
                if (productUrl == null || productUrl.isBlank()) productUrl = detailUrl;

                String rawTitle = attr(doc.selectFirst("meta[property=og:title]"), "content");
                String title = cleanTitle(rawTitle != null ? rawTitle : text(doc.selectFirst("h2")));

                String imageUrl = attr(doc.selectFirst("meta[property=og:image]"), "content");

                // 상단 헤더 블럭
                Element head = doc.selectFirst("div.end_head");

                // ⭐ 별점: div.score_area 안의 숫자
                BigDecimal rating = extractRating(doc);

                // ⬇️ 다운로드(=관심) 수: "관심 7억 2,445만 ..." 패턴
                Long downloadCount = extractInterestCountFromHead(head);

                // 💬 댓글 수: h3:matchesOwn(댓글) → span 숫자, 폴백으로 head의 "공유" 앞 숫자
                Long commentCount = extractCommentCount(doc, head);

                // 📚 상세정보 블럭
                Element infoUl = doc.selectFirst("ul.end_info li.info_lst > ul");

                // 📚 연재 상태
                String status = firstText(infoUl, "li.ing > span"); // "연재중"/"완결" 등

                // ✍️ 글/출판사
                String author = findInfoValue(infoUl, "글");
                String publisher = findInfoValue(infoUl, "출판사");

                // 🔞 이용가
                String ageRating = findAge(infoUl);

                // 🏷 장르
                List<String> genres = new ArrayList<>();
                if (infoUl != null) {
                    for (Element li : infoUl.select("> li")) {
                        String label = text(li.selectFirst("> span"));
                        if ("글".equals(label) || "출판사".equals(label) || "이용가".equals(label) || "연재중".equals(label)) {
                            continue;
                        }
                        Element a = li.selectFirst("a");
                        if (a != null) {
                            String g = a.text().trim();
                            if (!g.isEmpty() && !genres.contains(g)) genres.add(g);
                        }
                    }
                }

                // ==================== [수정된 부분 시작] ====================
                // 📝 시놉시스
                // "더보기"가 있는 경우, 숨겨진 전체 줄거리가 담긴 div와 짧은 줄거리가 담긴 div가 모두 존재합니다.
                // ._synopsis 클래스를 가진 요소가 여러 개일 수 있으므로, 마지막 요소를 선택하여 항상 전체 줄거리를 가져오도록 합니다.
                String synopsis = "";
                Elements synopsisElements = doc.select("div.end_dsc ._synopsis");
                if (!synopsisElements.isEmpty()) {
                    synopsis = text(synopsisElements.last());
                }
                // ==================== [수정된 부분 끝] ======================

                // productNo
                String titleId = extractQueryParam(productUrl, "productNo");

                // ===== raw payload 구성 =====
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

                // 저장 (hash로 중복 자동 스킵)
                collector.saveRaw(
                        "NaverSeries",
                        "WEBNOVEL",
                        payload,
                        titleId,
                        productUrl
                );
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
    private static String firstText(Element root, String css) {
        if (root == null) return null;
        Element el = root.selectFirst(css);
        return el == null ? null : el.text().trim();
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

    private static Long extractInterestCountFromHead(Element head) {
        if (head == null) return null;
        String t = head.text();
        // "관심 7억 2,445만 139.3만 공유" → 관심 수만 파싱
        Matcher m = Pattern.compile("관심\\s*(\\d+(?:\\.\\d+)?\\s*억(?:\\s*[\\d,\\.]+\\s*만)?|\\d+(?:\\.\\d+)?\\s*만|[\\d,]+)")
                .matcher(t);
        if (m.find()) return parseKoreanCount(m.group(1));
        return null;
    }

    private static Long extractCommentCount(Document doc, Element head) {
        Element h3 = doc.selectFirst("h3:matchesOwn(댓글)");
        if (h3 != null) {
            Element span = h3.selectFirst("span");
            if (span != null) {
                Long n = parseKoreanCount(span.text());
                if (n != null) return n;
            }
        }
        // 폴백: 헤더 텍스트 내 "공유" 앞 숫자
        if (head != null) {
            String t = head.text();
            Matcher m = Pattern.compile("관심\\s*(?:\\S+)\\s*(\\d+(?:\\.\\d+)?\\s*만|[\\d,]+)\\s*공유").matcher(t);
            if (m.find()) return parseKoreanCount(m.group(1));
        }
        return null;
    }

    /** "7억 2,445만", "139.3만", "1,393,475" 지원 */
    private static Long parseKoreanCount(String s) {
        if (s == null) return null;
        s = s.trim().replace(",", "");
        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*억(?:\\s*(\\d+(?:\\.\\d+)?)\\s*만)?").matcher(s);
        if (m.find()) {
            double eok = Double.parseDouble(m.group(1));
            double man = (m.group(2) != null) ? Double.parseDouble(m.group(2)) : 0.0;
            return Math.round(eok * 100_000_000 + man * 10_000);
        }
        m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*만").matcher(s);
        if (m.find()) return Math.round(Double.parseDouble(m.group(1)) * 10_000);
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
        // [독점], [완결] 등 태그 제거
        return raw.replaceAll("\\s*\\[[^\\]]+\\]\\s*", " ").replaceAll("\\s+", " ").trim();
    }

    private static String nz(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}