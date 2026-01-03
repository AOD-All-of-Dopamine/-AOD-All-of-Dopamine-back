package com.example.crawler.contents.Novel.KakaoPageNovel;

import com.example.crawler.ingest.CollectorService;
import com.example.crawler.util.InterruptibleSleep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class KakaoPageCrawler {

    private final CollectorService collector;
    private static final ObjectMapper OM = new ObjectMapper();
    private static final String GRAPHQL_API_URL = "https://bff-page.kakao.com/graphql";

    private static final String GRAPHQL_QUERY = """
    query staticLandingGenreSection($sectionId: ID!, $param: StaticLandingGenreParamInput!) {
      staticLandingGenreSection(sectionId: $sectionId, param: $param) {
        ... on StaticLandingGenreSection {
          isEnd
          groups {
            items {
              ... on PosterViewItem {
                scheme
              }
            }
          }
        }
      }
    }
    """;

    public KakaoPageCrawler(CollectorService collector) {
        this.collector = collector;
    }

    public int crawlToRaw(
            String sectionId, int categoryUid, String subcategoryUid,
            String sortType, boolean isComplete, String cookieString, int maxPages) throws Exception {

        int saved = 0;
        int page = 1;

        while (true) {
            // 인터럽트 체크 - 작업 취소 요청 확인
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("작업 인터럽트 감지, 크롤링 중단 (현재까지 " + saved + "개 저장)");
                return saved;
            }
            
            if (maxPages > 0 && page > maxPages) break;

            // ... (1, 2, 3 단계는 변경 없음) ...
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("categoryUid", categoryUid);
            paramMap.put("subcategoryUid", subcategoryUid);
            paramMap.put("sortType", sortType);
            paramMap.put("isComplete", isComplete);
            paramMap.put("screenUid", null);
            paramMap.put("page", page);

            Map<String, Object> variablesMap = new HashMap<>();
            variablesMap.put("sectionId", sectionId);
            variablesMap.put("param", paramMap);

            Map<String, Object> payload = Map.of(
                    "query", GRAPHQL_QUERY,
                    "variables", variablesMap
            );
            String jsonPayload = OM.writeValueAsString(payload);

            Connection.Response response = Jsoup.connect(GRAPHQL_API_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                    .referrer("https://page.kakao.com/")
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .requestBody(jsonPayload)
                    .method(Connection.Method.POST)
                    .ignoreContentType(true)
                    .timeout(20000)
                    .execute();

            String jsonResponse = response.body();

            Set<String> detailUrls = new LinkedHashSet<>();
            JsonNode root = OM.readTree(jsonResponse);
            JsonNode itemsNode = root.at("/data/staticLandingGenreSection/groups/0/items");

            if (itemsNode.isArray()) {
                for (JsonNode item : itemsNode) {
                    String scheme = item.path("scheme").asText(null);
                    if (scheme != null && scheme.contains("series_id=")) {
                        Matcher m = Pattern.compile("series_id=(\\d+)").matcher(scheme);
                        if (m.find()) {
                            detailUrls.add("https://page.kakao.com/content/" + m.group(1));
                        }
                    }
                }
            }

            if (detailUrls.isEmpty()) {
                System.out.println("페이지 " + page + "에서 더 이상 작품을 찾을 수 없어 종료합니다.");
                break;
            }

            // 4. 각 상세 페이지 크롤링 및 저장
            for (String detailUrl : detailUrls) {
                try {
                    Document doc = get(detailUrl, cookieString);
                    // ★★★★★ 수정된 부분: parseDetail에 cookieString을 전달 ★★★★★
                    KakaoPageNovelDTO dto = parseDetail(doc, detailUrl, cookieString);

                    Map<String, Object> dataToSave = new LinkedHashMap<>();
                    dataToSave.put("title", nz(dto.getTitle()));
                    dataToSave.put("author", nz(dto.getAuthor()));
                    dataToSave.put("synopsis", nz(dto.getSynopsis()));
                    dataToSave.put("imageUrl", nz(dto.getImageUrl()));
                    dataToSave.put("productUrl", nz(dto.getProductUrl()));
                    dataToSave.put("seriesId", nz(dto.getSeriesId()));
                    dataToSave.put("status", nz(dto.getStatus()));
                    dataToSave.put("publisher", nz(dto.getPublisher()));
                    dataToSave.put("ageRating", nz(dto.getAgeRating()));
                    dataToSave.put("genres", dto.getGenres());
                    dataToSave.put("keywords", dto.getKeywords());
                    dataToSave.put("rating", dto.getRating());
                    dataToSave.put("viewCount", dto.getViewCount());
                    dataToSave.put("commentCount", dto.getCommentCount());

                    collector.saveRaw("KakaoPage", "WEBNOVEL", dataToSave, dto.getSeriesId(), dto.getProductUrl());
                    saved++;
                } catch (Exception e) {
                    System.err.println("상세 페이지 처리 중 오류: " + detailUrl + " - " + e.getMessage());
                }
            }

            boolean isEnd = root.at("/data/staticLandingGenreSection/isEnd").asBoolean(true);
            if (isEnd) {
                System.out.println("API가 마지막 페이지라고 응답하여 종료합니다.");
                break;
            }

            page++;
            if (!InterruptibleSleep.sleep(1000)) {
                System.out.println("카카오페이지 크롤링 인터럽트 발생, 작업 중단");
                break;
            }
        }
        return saved;
    }

    /**
     * 상세 페이지 HTML을 파싱하여 DTO 객체를 생성하는 메소드. (수정됨)
     */
    public KakaoPageNovelDTO parseDetail(Document doc, String detailUrl, String cookieString) throws Exception {
        KakaoPageNovelDTO.KakaoPageNovelDTOBuilder b = KakaoPageNovelDTO.builder();

        // --- 1. 기본 페이지('홈' 탭) 정보 파싱 ---
        String productUrl = meta(doc, "property", "og:url");
        if (isBlank(productUrl)) productUrl = detailUrl;
        b.productUrl(productUrl);
        b.seriesId(extractSeriesIdFromPath(productUrl));

        b.imageUrl(meta(doc, "property", "og:image"));
        b.title(clean(meta(doc, "property", "og:title")));
        b.author(clean(meta(doc, "name", "author")));

        String synopsis = meta(doc, "property", "og:description");
        if (isBlank(synopsis)) synopsis = meta(doc, "name", "description");
        b.synopsis(clean(synopsis));

        List<String> keywords = new ArrayList<>();
        String kw = meta(doc, "name", "keywords");
        if (!isBlank(kw)) {
            keywords.addAll(Arrays.stream(kw.split("[,;]")).map(String::trim).toList());
        }
        b.keywords(keywords);

        Element statsRow = selectFirstSafe(doc, "div.flex.h-16pxr.items-center.justify-center");
        if (statsRow != null) {
            Element genreSpan = selectFirstSafe(statsRow, "> div:nth-child(1) > div > span:nth-child(3)");
            b.genres(Arrays.stream(text(genreSpan).split("[,\\s]+")).map(String::trim).toList());

            Element viewSpan = selectFirstSafe(statsRow, "> div:nth-child(2) > span");
            b.viewCount(parseKoreanCount(text(viewSpan)));

            Element ratingSpan = selectFirstSafe(statsRow, "> div:nth-child(3) > span");
            String num = text(ratingSpan).replaceAll("[^0-9.]", "");
            if (!isBlank(num)) {
                try { b.rating(new BigDecimal(num)); } catch (Exception ignored) {}
            }
        }
        Element statusWrap = selectFirstSafe(doc, "div.mt-6pxr.flex.items-center");
        b.status(normalizeStatus(text(statusWrap)));


        // ★★★★★ '정보' 탭 추가 정보 파싱 (새로 추가된 부분) ★★★★★
        try {
            // 1. '정보' 탭 URL 생성 (기존 URL에서 쿼리 파라미터 제거 후 추가)
            String aboutUrl = detailUrl.split("\\?")[0] + "?tab_type=about";

            // 2. '정보' 탭 페이지를 새로 요청하여 HTML 가져오기
            Document aboutDoc = get(aboutUrl, cookieString);

            // 3. '정보' 탭에서 발행자, 연령등급 파싱
            // '발행자'라는 텍스트를 가진 span을 포함하는 div를 찾고, 그 안의 두 번째 span의 텍스트를 가져옴
            Element publisherDiv = selectFirstSafe(aboutDoc, "div:has(span:containsOwn(발행자))");
            if (publisherDiv != null) {
                b.publisher(clean(text(selectFirstSafe(publisherDiv, "span:last-of-type, span.text-el-70"))));
            }

            // '연령등급'이라는 텍스트를 가진 span을 포함하는 div를 찾고, 그 안의 두 번째 span의 텍스트를 가져옴
            Element ageRatingDiv = selectFirstSafe(aboutDoc, "div:has(span:containsOwn(연령등급))");
            if (ageRatingDiv != null) {
                b.ageRating(clean(text(selectFirstSafe(ageRatingDiv, "span:last-of-type, span.text-el-70"))));
            }

        } catch (Exception e) {
            System.err.println("'정보' 탭 파싱 중 오류 발생: " + detailUrl + " - " + e.getMessage());
            // '정보' 탭 파싱에 실패하더라도 이미 수집한 기본 정보는 유지됨
        }

        return b.build();
    }

    // ===================== Helper Methods (변경 없음) =====================
    private Document get(String url, String cookieString) throws Exception {
        var conn = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .referrer("https://page.kakao.com/")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8")
                .timeout(15000);
        if (!isBlank(cookieString)) conn.header("Cookie", cookieString);
        return conn.get();
    }

    private String extractSeriesIdFromPath(String url) {
        if (isBlank(url)) return null;
        Matcher m = Pattern.compile("/content/(\\d+)").matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private Long parseKoreanCount(String raw) {
        if (isBlank(raw)) return null;
        raw = raw.replace(",", "").trim();
        Matcher m = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)(억|만|천)?").matcher(raw);
        if (!m.find()) return null;
        double v = Double.parseDouble(m.group(1));
        String unit = m.group(2);
        if ("억".equals(unit)) v *= 100_000_000d;
        else if ("만".equals(unit)) v *= 10_000d;
        else if ("천".equals(unit)) v *= 1_000d;
        return (long) v;
    }

    private static String normalizeStatus(String s) {
        if (s == null) return null;
        return s.replace('\u00A0', ' ').replaceAll("\\s*·\\s*", " ").replaceAll("\\s{2,}", " ").trim();
    }

    private static Element selectFirstSafe(Element root, String css) {
        if (root == null || isBlank(css)) return null;
        try {
            return root.selectFirst(css);
        } catch (org.jsoup.select.Selector.SelectorParseException e) {
            return null;
        }
    }

    private static String meta(Document doc, String attr, String nameOrProp) {
        Element e = doc.selectFirst("meta[" + attr + "=\"" + nameOrProp + "\"]");
        return e != null ? e.attr("content") : null;
    }

    private static String text(Element e) { return e == null ? null : e.text(); }
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String clean(String s) { return s == null ? null : s.replaceAll("\\s+", " ").trim(); }
    private static String nz(String s) { return s == null ? "" : s; }
}

