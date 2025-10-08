package com.example.AOD.Novel.KakaoPageNovel;

import com.example.AOD.ingest.CollectorService;
import com.fasterxml.jackson.core.type.TypeReference;
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

    // API 요청에 필요한 GraphQL 쿼리 (필요한 최소 정보만 요청하도록 간소화 가능)
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

    /**
     * GraphQL API를 사용하여 목록을 크롤링하는 최신 메소드.
     * @param sectionId 고정된 섹션 ID (예: "static-landing-Genre-section-Landing-11-0-UPDATE-false")
     * @param categoryUid 카테고리 ID (예: 11)
     * @param subcategoryUid 서브카테고리 ID (예: "0")
     * @param sortType 정렬 타입 (예: "UPDATE")
     * @param isComplete 완결 여부
     * @param cookieString 로그인 쿠키 (필요 시)
     * @param maxPages 최대 크롤링할 페이지 수
     * @return 저장된 아이템 개수
     * @throws Exception
     */
    public int crawlToRaw(
            String sectionId, int categoryUid, String subcategoryUid,
            String sortType, boolean isComplete, String cookieString, int maxPages) throws Exception {

        int saved = 0;
        int page = 1;

        while (true) {
            if (maxPages > 0 && page > maxPages) break;

            // 1. GraphQL 요청 Payload 생성
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

            // 2. Jsoup으로 POST 요청
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

            // 3. 응답 JSON에서 seriesId 추출
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
                    KakaoPageNovelDTO dto = parseDetail(doc, detailUrl);

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
                    System.err.println("상세 페이지 처리 중 오류: " + detailUrl);
                }
            }

            // 5. 종료 조건 확인
            boolean isEnd = root.at("/data/staticLandingGenreSection/isEnd").asBoolean(true);
            if (isEnd) {
                System.out.println("API가 마지막 페이지라고 응답하여 종료합니다.");
                break;
            }

            page++;
            Thread.sleep(1000); // 서버 부하 방지를 위한 대기 시간
        }
        return saved;
    }

    /**
     * 상세 페이지 HTML을 파싱하여 DTO 객체를 생성하는 메소드.
     */
    public KakaoPageNovelDTO parseDetail(Document doc, String detailUrl) {
        KakaoPageNovelDTO.KakaoPageNovelDTOBuilder b = KakaoPageNovelDTO.builder();

        String productUrl = meta(doc, "property", "og:url");
        if (isBlank(productUrl)) productUrl = detailUrl;
        b.productUrl(productUrl);
        b.seriesId(extractSeriesIdFromPath(productUrl));

        b.imageUrl(meta(doc, "property", "og:image"));

        String title = meta(doc, "property", "og:title");
        if (isBlank(title)) {
            title = text(selectFirstSafe(doc, "span.font-large3-bold"));
        }
        b.title(clean(title));

        String author = meta(doc, "name", "author");
        if (isBlank(author)) {
            author = text(selectFirstSafe(doc, "span.font-small2.mb-6pxr"));
        }
        b.author(clean(author));

        String synopsis = meta(doc, "property", "og:description");
        if (isBlank(synopsis)) synopsis = meta(doc, "name", "description");
        if (isBlank(synopsis)) {
            synopsis = text(selectFirstSafe(doc, "div > div > div > div > span[class*='whitespace-pre-wrap']"));
        }
        b.synopsis(clean(synopsis));

        List<String> keywords = new ArrayList<>();
        String kw = meta(doc, "name", "keywords");
        if (!isBlank(kw)) {
            keywords.addAll(Arrays.asList(kw.split("[,;]")));
        }
        b.keywords(keywords);

        Element statsRow = selectFirstSafe(doc, "div.flex.h-16pxr.items-center.justify-center");
        List<String> genres = new ArrayList<>();
        Long viewCount = null;
        BigDecimal rating = null;
        if (statsRow != null) {
            Element genreSpan = selectFirstSafe(statsRow, "> div:nth-child(1) > div > span:nth-child(3)");
            String g = text(genreSpan);
            if (!isBlank(g)) {
                genres.addAll(Arrays.asList(g.split("[,\\s]+")));
            }

            Element viewSpan = selectFirstSafe(statsRow, "> div:nth-child(2) > span");
            viewCount = parseKoreanCount(text(viewSpan));

            Element ratingSpan = selectFirstSafe(statsRow, "> div:nth-child(3) > span");
            String num = text(ratingSpan).replaceAll("[^0-9.]", "");
            if (!isBlank(num)) {
                try {
                    rating = new BigDecimal(num);
                } catch (Exception ignored) {
                }
            }
        }
        b.genres(genres);
        b.viewCount(viewCount);
        b.rating(rating);

        Element statusWrap = selectFirstSafe(doc, "div.mt-6pxr.flex.items-center");
        String status = null;
        if (statusWrap != null) {
            status = normalizeStatus(statusWrap.text());
        }
        b.status(status);

        return b.build();
    }

    // ===================== Helper Methods =====================

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
        return s.replace('\u00A0', ' ')
                .replaceAll("\\s*·\\s*", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
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

    private static String text(Element e) {
        return e == null ? null : e.text();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String clean(String s) {
        return s == null ? null : s.replaceAll("\\s+", " ").trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}