package com.example.AOD.Novel.KakaoPageNovel;


import com.example.AOD.ingest.CollectorService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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

        // === productUrl & seriesId (메타 → 요청 URL) ===
        String productUrl = meta(doc, "property", "og:url");
        if (isBlank(productUrl)) productUrl = detailUrl;
        b.productUrl(productUrl);
        b.seriesId(extractSeriesIdFromPath(productUrl));

        // === 대표 이미지 (메타) ===
        b.imageUrl(meta(doc, "property", "og:image"));

        // === 제목 (메타 → CSS) ===
        String title = meta(doc, "property", "og:title");
        if (isBlank(title)) {
            title = text(selectFirstSafe(doc,
                    "#__next > div > div.flex.w-full.grow.flex-col.px-122pxr > div.flex.h-full.flex-1.flex-col > " +
                            "div > div.flex.flex-col.mb-28pxr.w-320pxr > div.rounded-t-12pxr.bg-bg-a-20 > div > " +
                            "div.relative.px-18pxr.text-center.bg-bg-a-20.mt-24pxr > a > div > " +
                            "span.font-large3-bold.mb-3pxr.text-ellipsis.break-all.text-el-70.line-clamp-2"));
        }
        b.title(clean(title));

        // === 작가 (메타 → CSS) ===
        String author = meta(doc, "name", "author");
        if (isBlank(author)) {
            author = text(selectFirstSafe(doc,
                    "#__next > div > div.flex.w-full.grow.flex-col.px-122pxr > div.flex.h-full.flex-1.flex-col > " +
                            "div > div.flex.flex-col.mb-28pxr.w-320pxr > div.rounded-t-12pxr.bg-bg-a-20 > div > " +
                            "div.relative.px-18pxr.text-center.bg-bg-a-20.mt-24pxr > a > div > " +
                            "span.font-small2.mb-6pxr.text-ellipsis.text-el-70.opacity-70.break-word-anywhere.line-clamp-2"));
        }
        b.author(clean(author));

        // === 줄거리 (메타 → CSS) ===
        String synopsis = meta(doc, "property", "og:description");
        if (isBlank(synopsis)) synopsis = meta(doc, "name", "description");
        if (isBlank(synopsis)) {
            synopsis = text(selectFirstSafe(doc,
                    "#__next > div > div.flex.w-full.grow.flex-col.px-122pxr > div.flex.h-full.flex-1.flex-col > " +
                            "div > div.flex.flex-col.overflow-hidden.mb-28pxr.ml-4px.w-632pxr.rounded-12pxr > div.flex.flex-1.flex-col > div > div > " +
                            "div.flex.w-full.flex-col.space-y-4pxr.rounded-b-12pxr.bg-bg-a-20 > div > div.flex.w-full.flex-col.items-center.overflow-hidden > div > div > span"));
            if (isBlank(synopsis)) {
                synopsis = text(selectFirstSafe(doc, "div:matchesOwn(소개|시놉시스) ~ div, section:has(h3:matchesOwn(소개)) p, .synopsis, .summary"));
            }
        }
        b.synopsis(clean(synopsis));

        // === 키워드 (메타 → 그대로 리스트화) ===
        List<String> keywords = new ArrayList<>();
        String kw = meta(doc, "name", "keywords");
        if (!isBlank(kw)) {
            for (String s : kw.split("[,;]")) {
                s = s.trim();
                if (!s.isEmpty() && !keywords.contains(s)) keywords.add(s);
            }
        }
        b.keywords(keywords);

        // === 상세 헤더의 통계 줄(장르/조회수/별점): CSS로만 ===
        Element statsRow = selectFirstSafe(doc,
                "#__next > div > div.flex.w-full.grow.flex-col.px-122pxr > div.flex.h-full.flex-1.flex-col > " +
                        "div > div.flex.flex-col.mb-28pxr.w-320pxr > div.rounded-t-12pxr.bg-bg-a-20 > div > " +
                        "div.relative.px-18pxr.text-center.bg-bg-a-20.mt-24pxr > a > div > " +
                        "div.flex.h-16pxr.items-center.justify-center");

        // 장르: div:nth-child(1) > div > span:nth-child(3)
        List<String> genres = new ArrayList<>();
        if (statsRow != null) {
            Element genreSpan = selectFirstSafe(statsRow, "> div:nth-child(1) > div > span:nth-child(3)");
            String g = text(genreSpan);
            if (!isBlank(g)) {
                for (String t : g.split("[,\\s]+")) {
                    t = t.trim();
                    if (!t.isEmpty() && !genres.contains(t)) genres.add(t);
                }
            }
        }
        b.genres(genres);

        // 조회수: div:nth-child(2) > span  (+ 숫자/한국어 단위 폴백)
        Long viewCount = null;
        if (statsRow != null) {
            Element viewSpan = selectFirstSafe(statsRow, "> div:nth-child(2) > span");
            viewCount = parseKoreanCount(text(viewSpan));
            if (viewCount == null) {
                for (Element sp : statsRow.select("span")) {
                    Long v = parseKoreanCount(sp.text());
                    if (v != null) { viewCount = v; break; }
                }
            }
        }
        b.viewCount(viewCount);

        // 별점: div:nth-child(3) > span (숫자만 추출)
        BigDecimal rating = null;
        if (statsRow != null) {
            Element ratingSpan = selectFirstSafe(statsRow, "> div:nth-child(3) > span");
            String num = text(ratingSpan);
            if (!isBlank(num)) {
                num = num.replaceAll("[^0-9.]", "");
                if (!isBlank(num)) {
                    try { rating = new BigDecimal(num); } catch (Exception ignore) {}
                }
            }
        }
        b.rating(rating);

        // === 연재주기(상태): CSS로만 ===
        Element statusWrap = selectFirstSafe(doc,
                "#__next > div > div.flex.w-full.grow.flex-col.px-122pxr > div.flex.h-full.flex-1.flex-col > " +
                        "div > div.flex.flex-col.mb-28pxr.w-320pxr > div.rounded-t-12pxr.bg-bg-a-20 > div > " +
                        "div.relative.px-18pxr.text-center.bg-bg-a-20.mt-24pxr > a > div > " +
                        "div.mt-6pxr.flex.items-center");

        String status = null;
        if (statusWrap != null) {
            Element sp = selectFirstSafe(statusWrap, "> span, span:first-of-type");
            status = clean(text(sp));
            if (isBlank(status)) status = clean(statusWrap.text());
            status = normalizeStatus(status);
        }
        b.status(status);

        // 나머지 필드(출판사/이용가/댓글수 등)는 필요 시 확장
        return b.build();
    }

    /** Jsoup가 이해 못하는 CSS가 들어와도 null을 반환하게 하는 안전 헬퍼 */
    private static Element selectFirstSafe(Element root, String css) {
        if (root == null || isBlank(css)) return null;
        try {
            return root.selectFirst(css);
        } catch (org.jsoup.select.Selector.SelectorParseException e) {
            return null;
        }
    }
    /* ===================== 새로 추가/교체된 부분 ===================== */

    // meta 태그 읽기: attr=name|property, 값=nameOrProp 일치하는 첫 요소의 content 반환
    private static String meta(Document doc, String attr, String nameOrProp) {
        Element e = doc.selectFirst("meta[" + attr + "=\"" + nameOrProp + "\"]");
        return e != null ? e.attr("content") : null;
    }


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

    // 연재주기 문자열 정리: 중점(·), NBSP 등 제거/정규화
    private static String normalizeStatus(String s) {
        if (s == null) return null;
        return s
                .replace('\u00A0', ' ')           // NBSP → space
                .replaceAll("\\s*·\\s*", " ")     // 중점 구분자 제거
                .replaceAll("\\s{2,}", " ")       // 다중 공백 축소
                .trim();
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
