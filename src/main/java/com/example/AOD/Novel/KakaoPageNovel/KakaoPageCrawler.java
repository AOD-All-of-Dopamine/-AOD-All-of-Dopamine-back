package com.example.AOD.Novel.KakaoPageNovel;


import com.example.AOD.ingest.CollectorService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class KakaoPageCrawler {

    private final CollectorService collector;
    private static final ObjectMapper OM = new ObjectMapper();

    public KakaoPageCrawler(CollectorService collector) {
        this.collector = collector;
    }

    public int crawlToRaw(String baseListUrl, String cookieString, int maxPages) throws Exception {
        int saved = 0;
        int page = 1;

        while (true) {
            if (maxPages > 0 && page > maxPages) break;

            // 안전한 페이지 URL 계산 (base가 {page} 플레이스홀더나 ...page= 로 끝나는 경우만 적용)
            String listUrl = pageUrl(baseListUrl, page);
            Document listDoc = get(listUrl, cookieString);

            // === NEW: __NEXT_DATA__와 raw HTML에서 seriesId 모으기 ===
            Set<String> detailUrls = scanDetailUrlsFromNextData(listDoc);
            if (detailUrls.isEmpty()) {
                detailUrls = scanDetailUrlsFallback(listDoc); // regex 폴백
            }
            if (detailUrls.isEmpty()) {
                // 더 이상 수집할 게 없으면 종료
                break;
            }

            for (String detailUrl : detailUrls) {
                Document doc;
                try {
                    doc = get(detailUrl, cookieString);
                } catch (org.jsoup.HttpStatusException e) {
                    // 404 등은 스킵
                    continue;
                }

                KakaoPageNovelDTO dto = parseDetail(doc, detailUrl);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("title", nz(dto.getTitle()));
                payload.put("author", nz(dto.getAuthor()));
                payload.put("synopsis", nz(dto.getSynopsis()));
                payload.put("imageUrl", nz(dto.getImageUrl()));
                payload.put("productUrl", nz(dto.getProductUrl()));
                payload.put("seriesId", nz(dto.getSeriesId()));
                payload.put("status", nz(dto.getStatus()));
                payload.put("publisher", nz(dto.getPublisher()));
                payload.put("ageRating", nz(dto.getAgeRating()));
                payload.put("genres", dto.getGenres());
                payload.put("keywords", dto.getKeywords());
                payload.put("rating", dto.getRating());
                payload.put("viewCount", dto.getViewCount());
                payload.put("commentCount", dto.getCommentCount());

                collector.saveRaw("KakaoPage", "WEBNOVEL", payload, dto.getSeriesId(), dto.getProductUrl());
                saved++;
            }

            page++;
        }
        return saved;
    }

    // === 새 상세 파서(기존 parseDetail 유지 시 이 시그니처만 맞추면 OK) ===
    public KakaoPageNovelDTO parseDetail(Document doc, String detailUrl) {
        KakaoPageNovelDTO.KakaoPageNovelDTOBuilder b = KakaoPageNovelDTO.builder();
        String productUrl = attr(doc.selectFirst("meta[property=og:url]"), "content");
        if (isBlank(productUrl)) productUrl = detailUrl;
        b.productUrl(productUrl);

        // /content/{id}에서 id 추출
        String seriesId = extractSeriesIdFromPath(productUrl);
        b.seriesId(seriesId);

        String title = attr(doc.selectFirst("meta[property=og:title]"), "content");
        if (isBlank(title)) title = text(doc.selectFirst("h1, h2"));
        b.title(clean(title));

        String imageUrl = attr(doc.selectFirst("meta[property=og:image]"), "content");
        b.imageUrl(imageUrl);

        // 별점 (img[alt=별점] + span)
        b.rating(extractRating(doc));

        // 뷰 수 (보이는 텍스트 없으면 null)
        b.viewCount(extractKoreanCountNearHeader(doc));

        // 댓글 수 (보이는 텍스트 없으면 null)
        b.commentCount(extractCommentCount(doc));

        // 상태/작가/출판사/이용가/장르/키워드/시놉시스
        b.status(firstText(doc, "span:matchesOwn(완결|연재)"));
        String author = findLabeledValue(doc, "작가");
        if (isBlank(author)) author = findAuthorFallback(doc);
        b.author(author);
        b.publisher(findLabeledValue(doc, "출판사"));
        b.ageRating(findLabeledValue(doc, "이용가|연령|등급"));
        b.genres(extractChipsNearLabel(doc, "장르"));
        b.keywords(extractChipsNearLabel(doc, "키워드"));
        String synopsis = text(doc.selectFirst("div:matchesOwn(소개|시놉시스) ~ div, section:has(h3:matchesOwn(소개)) p, .synopsis, .summary"));
        if (isBlank(synopsis)) synopsis = attr(doc.selectFirst("meta[name=description]"), "content");
        b.synopsis(clean(synopsis));
        return b.build();
    }

    /* ===================== 새로 추가/교체된 부분 ===================== */

    /** listDoc의 __NEXT_DATA__에서 seriesId를 모아 /content/{id} URL을 만든다 */
    private Set<String> scanDetailUrlsFromNextData(Document listDoc) {
        Set<String> urls = new LinkedHashSet<>();
        Element next = listDoc.selectFirst("script#__NEXT_DATA__");
        if (next == null) return urls;
        try {
            Map<String,Object> json = OM.readValue(next.data(), new TypeReference<>() {});
            // 모든 노드 순회하며 seriesId(int/long/str)을 수집
            Deque<Object> dq = new ArrayDeque<>();
            dq.add(json);
            while (!dq.isEmpty()) {
                Object cur = dq.poll();
                if (cur instanceof Map<?,?> m) {
                    for (var e : m.entrySet()) {
                        String k = String.valueOf(e.getKey());
                        Object v = e.getValue();
                        if ("seriesId".equals(k) && v != null) {
                            String sid = String.valueOf(v).replaceAll("[^0-9]", "");
                            if (!sid.isEmpty()) urls.add("https://page.kakao.com/content/" + sid);
                        }
                        dq.add(v);
                    }
                } else if (cur instanceof List<?> arr) {
                    for (Object v : arr) dq.add(v);
                }
            }
        } catch (Exception ignore) {}
        return urls;
    }

    /** 폴백: 원시 HTML 문자열에서 /content/{id} 패턴을 직접 찾는다 */
    private Set<String> scanDetailUrlsFallback(Document listDoc) {
        Set<String> urls = new LinkedHashSet<>();
        String html = listDoc.outerHtml();
        var m = Pattern.compile("/content/(\\d{5,})").matcher(html);
        while (m.find()) {
            urls.add("https://page.kakao.com/content/" + m.group(1));
        }
        return urls;
    }

    /** page 파라미터를 안전하게 붙이기 (없으면 그대로 반환) */
    private String pageUrl(String base, int page) {
        if (base == null) return null;
        if (base.contains("{page}")) return base.replace("{page}", String.valueOf(page));
        if (base.matches(".*[?&]page=$")) return base + page;
        if (base.matches(".*[?&]page=\\d+$")) return base.replaceAll("page=\\d+", "page=" + page);
        // landing/genre/11 처럼 page가 없는 URL은 그대로 사용
        return base;
    }

    private String extractSeriesIdFromPath(String url) {
        if (isBlank(url)) return null;
        Matcher m = Pattern.compile("/content/(\\d+)").matcher(url);
        return m.find() ? m.group(1) : null;
    }
    /* ===================== Helpers ===================== */

    private Document get(String url, String cookieString) throws Exception {
        var conn = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .referrer("https://page.kakao.com/")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8")
                .timeout(15000);
        if (!isBlank(cookieString)) conn.header("Cookie", cookieString);
        return conn.get();
    }

    private Set<String> scanDetailUrls(Document listDoc, String baseListUrl) {
        Set<String> urls = new LinkedHashSet<>();

        // 1) 정규표현식으로 series_id 패턴 잡기
        Matcher m = Pattern.compile("/content\\?series_id=([0-9]{5,})").matcher(listDoc.html());
        while (m.find()) {
            String path = m.group(0);
            String abs = "https://page.kakao.com" + path;
            urls.add(abs);
        }

        // 2) a[href*='/content?series_id=']가 있으면 그대로
        for (Element a : listDoc.select("a[href*='/content?series_id=']")) {
            String href = a.attr("href");
            if (!href.startsWith("http")) href = "https://page.kakao.com" + href;
            urls.add(href);
        }
        return urls;
    }

    private String appendPage(String url, int page) {
        if (url.contains("page=")) return url.replaceAll("page=\\d+", "page=" + page);
        return url + (url.contains("?") ? "&" : "?") + "page=" + page;
    }

    private static String firstText(Document doc, String cssQuery) {
        Element el = doc.selectFirst(cssQuery);
        return el != null ? el.text() : null;
        // This is just a shorthand for text(doc.selectFirst(cssQuery))
    }

    private BigDecimal extractRating(Document doc) {
        Element el = doc.selectFirst("img[alt=별점] + span, [aria-label=별점] + span");
        if (el == null) return null;
        String num = el.text().replaceAll("[^0-9.]", "");
        if (isBlank(num)) return null;
        try { return new BigDecimal(num); } catch (Exception ignore) { return null; }
    }

    private Long extractKoreanCountNearHeader(Document doc) {
        // 헤더의 카운트 블럭에서 '억/만' 등 한국어 단위를 처리
        // 우선 아이콘 옆 첫 번째 숫자 후보
        for (Element span : doc.select("div.flex.items-center span, span")) {
            String s = span.text();
            if (s == null) continue;
            Long val = parseKoreanCount(s);
            if (val != null) return val;
        }
        return null;
    }

    private Long extractCommentCount(Document doc) {
        // "댓글" 이라는 텍스트 근방의 숫자
        Element h = doc.selectFirst("*:matchesOwn(^\\s*댓글\\s*$), *:matchesOwn(댓글\\s*[0-9,.만억]+)");
        if (h != null) {
            Matcher m = Pattern.compile("([0-9][0-9,\\.]*)(만|억)?").matcher(h.text());
            if (m.find()) return scaleKoreanNumber(m.group(1), m.group(2));
        }
        // 아이콘/버튼 근처 폴백
        for (Element e : doc.select("*:matchesOwn(댓글) ~ *")) {
            Long v = parseKoreanCount(e.text());
            if (v != null) return v;
        }
        return null;
    }

    private List<String> extractChipsNearLabel(Document doc, String label) {
        // "장르", "키워드" 레이블 근처의 a/chip 수집
        List<String> out = new ArrayList<>();
        for (Element lab : doc.select("span:matchesOwn(" + label + ")")) {
            Element group = lab.parent();
            if (group != null) {
                for (Element a : group.select("a, button, span")) {
                    String t = a.text().trim();
                    if (!t.isEmpty() && t.length() <= 20 && !t.equals(label) && !out.contains(t)) out.add(t);
                }
            }
        }
        // 폴백: 태그/칩 공용 클래스 검색
        if (out.isEmpty()) {
            for (Element a : doc.select("a[href*='genre'], a[href*='category'], a[href*='keyword']")) {
                String t = a.text().trim();
                if (!t.isEmpty() && !out.contains(t)) out.add(t);
            }
        }
        return out;
    }

    private String findLabeledValue(Document doc, String labelRegex) {
        // "작가", "출판사", "이용가" 같은 레이블 바로 옆 값
        for (Element sp : doc.select("span:matchesOwn(" + labelRegex + ")")) {
            Element p = sp.parent();
            if (p != null) {
                Element a = p.selectFirst("a");
                if (a != null && !isBlank(a.text())) return a.text().trim();
                Element next = sp.nextElementSibling();
                if (next != null && !isBlank(next.text())) return next.text().trim();
            }
        }
        return null;
    }

    private String findAuthorFallback(Document doc) {
        // 작가가 링크로 표기되지 않은 케이스 대비
        for (Element e : doc.select("*:matchesOwn(작가)")) {
            String s = e.parent() != null ? e.parent().text() : "";
            if (s != null) {
                String t = s.replaceAll(".*작가\\s*", "").replaceAll("\\s*\\|.*", "").trim();
                if (!t.isEmpty() && t.length() < 30) return t;
            }
        }
        return null;
    }

    private Long parseKoreanCount(String raw) {
        if (isBlank(raw)) return null;
        raw = raw.replace(",", "").trim();
        Matcher m = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)(억|만|천)?").matcher(raw);
        if (!m.find()) return null;
        return scaleKoreanNumber(m.group(1), m.group(2));
    }

    private Long scaleKoreanNumber(String num, String unit) {
        double v = Double.parseDouble(num);
        if ("억".equals(unit)) v *= 100_000_000d;
        else if ("만".equals(unit)) v *= 10_000d;
        else if ("천".equals(unit)) v *= 1_000d;
        return (long) Math.floor(v);
    }

    private static String extractQueryParam(String url, String key) {
        if (isBlank(url)) return null;
        Matcher m = Pattern.compile("[?&]" + key + "=([^&]+)").matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private static String attr(Element e, String k) { return e == null ? null : e.attr(k); }
    private static String text(Element e) { return e == null ? null : e.text(); }
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String clean(String s){ return s == null ? null : s.replaceAll("\\s+", " ").trim(); }
    private static String nz(String s){ return s == null ? "" : s; }
}
