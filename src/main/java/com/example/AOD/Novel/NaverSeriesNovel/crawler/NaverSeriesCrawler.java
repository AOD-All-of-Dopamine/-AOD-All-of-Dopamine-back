package com.example.AOD.Novel.NaverSeriesNovel.crawler;

import com.example.AOD.Novel.NaverSeriesNovel.dto.NaverSeriesNovelDTO;
import com.example.AOD.ingest.CollectorService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Naver Series(웹소설) 크롤러
 * - 역할: 원본 페이지에서 필드 추출 → 평평한 payload(Map) 구성 → raw_items 에 저장
 * - 변환/업서트는 배치에서 수행
 */
@Component
public class NaverSeriesCrawler {

    private final CollectorService collector;

    public NaverSeriesCrawler(CollectorService collector) {
        this.collector = collector;
    }

    /**
     * 목록 페이지를 순회하여 상세 파싱 → raw_items 저장
     * @param baseListUrl 예: https://series.naver.com/novel/top100List.series?rankingTypeCode=ALL&categoryCode=ALL&page=
     * @param cookieString 필요시 전달(로그인/성인 접근)
     * @param maxPages 0 또는 음수면 끝까지
     * @return 저장 건수
     */
    public int crawlToRaw(String baseListUrl, String cookieString, int maxPages) throws Exception {
        int saved = 0;
        int page = 1;

        while (true) {
            if (maxPages > 0 && page > maxPages) break;

            String url = baseListUrl + page;
            Document listDoc = get(url, cookieString);

            Elements items = listDoc.select("ul.lst_thum > li, ul#content > li"); // 서비스 DOM에 맞춰 조정
            if (items.isEmpty()) break;

            for (Element li : items) {
                Element a = li.selectFirst("a.pic, a[href*='/novel/detail.series']");
                if (a == null) continue;

                String detailUrl = a.attr("href");
                if (!detailUrl.startsWith("http")) {
                    detailUrl = "https://series.naver.com" + detailUrl;
                }

                NaverSeriesNovelDTO dto = parseDetail(detailUrl, cookieString);

                // 목록에서 얻을 수 있는 추가 값 보강
                if (dto.getImageUrl() == null) {
                    Element img = li.selectFirst("img");
                    if (img != null) dto.setImageUrl(img.absUrl("src"));
                }
                if (dto.getProductUrl() == null) {
                    dto.setProductUrl(detailUrl);
                }

                // === 평평한 raw payload 구성 ===
                Map<String,Object> payload = new LinkedHashMap<>();
                payload.put("title", dto.getTitle());
                payload.put("author", dto.getAuthor());
                payload.put("translator", dto.getTranslator());
                payload.put("synopsis", dto.getSynopsis());
                payload.put("imageUrl", dto.getImageUrl());
                payload.put("productUrl", dto.getProductUrl());
                payload.put("titleId", dto.getTitleId());
                payload.put("weekday", dto.getWeekday());
                payload.put("episodeCount", dto.getEpisodeCount());
                payload.put("status", dto.getStatus());
                payload.put("startedAt", dto.getStartedAt() == null ? null : dto.getStartedAt().toString());
                payload.put("publisher", dto.getPublisher());
                payload.put("ageRating", dto.getAgeRating());
                payload.put("genres", dto.getGenres());

                // === raw_items 저장 (중복은 hash로 자동 무시) ===
                collector.saveRaw(
                        "NaverSeries",             // platformName
                        "WEBNOVEL",                // domain
                        payload,
                        dto.getTitleId(),          // platformSpecificId
                        dto.getProductUrl()        // url
                );
                saved++;
            }

            // 다음 페이지 감지(없으면 종료)
            Element nextBtn = listDoc.selectFirst("a.next, a.pg_next, a.btn_next");
            if (nextBtn == null) break;
            page++;
        }

        return saved;
    }

    /** 상세 페이지 파싱 → DTO */
    public NaverSeriesNovelDTO parseDetail(String detailUrl, String cookieString) throws Exception {
        Document doc = get(detailUrl, cookieString);
        NaverSeriesNovelDTO dto = new NaverSeriesNovelDTO();
        dto.setProductUrl(detailUrl);

        // 제목
        Element titleEl = doc.selectFirst("h2.tit, h3.detail_tit, .detail_view h2, meta[property='og:title']");
        dto.setTitle(titleEl != null ? textOrContent(titleEl) : null);

        // 이미지
        Element imgEl = doc.selectFirst("div.pic_area img, .detail_view .pic img, meta[property='og:image']");
        dto.setImageUrl(imgEl != null ? absSrcOrContent(imgEl, doc) : null);

        // 소개
        Element descEl = doc.selectFirst(".info .desc, .detail_info .summary, meta[name='description']");
        dto.setSynopsis(descEl != null ? textOrContent(descEl) : null);

        // 작가/번역/출판사
        Element authorEl = doc.selectFirst(".info .author a, .detail_info .author a, .author");
        dto.setAuthor(authorEl != null ? authorEl.text().trim() : null);

        Element translatorEl = doc.selectFirst(".info .translator a, .detail_info .translator a, .translator");
        if (translatorEl != null) dto.setTranslator(translatorEl.text().trim());

        Element publisherEl = doc.selectFirst(".info .publisher a, .detail_info .publisher a, .publisher");
        if (publisherEl != null) dto.setPublisher(publisherEl.text().trim());

        // 연재 정보
        Element weekdayEl = doc.selectFirst(".info .weekday, .detail_info .weekday");
        if (weekdayEl != null) dto.setWeekday(weekdayEl.text().trim());

        Element statusEl = doc.selectFirst(".info .status, .detail_info .status");
        if (statusEl != null) dto.setStatus(statusEl.text().trim());

        Element episodeEl = doc.selectFirst(".info .count, .detail_info .episode_count");
        if (episodeEl != null) {
            String digits = episodeEl.text().replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) dto.setEpisodeCount(Integer.parseInt(digits));
        }

        // 시작일
        Element startEl = doc.selectFirst(".info .start_date, .detail_info .date");
        if (startEl != null) dto.setStartedAt(parseDate(startEl.text()));

        // 연령 등급
        Element ageEl = doc.selectFirst(".info .age, .detail_info .age");
        if (ageEl != null) dto.setAgeRating(ageEl.text().trim());

        // 장르
        for (Element g : doc.select(".genre, .genre a, .badge.genre, .info .category a")) {
            String tg = g.text().trim();
            if (!tg.isEmpty() && !dto.getGenres().contains(tg)) dto.getGenres().add(tg);
        }

        // 내부 ID (productNo=123456 등)
        String idByQuery = detailUrl.replaceAll(".*(?:productNo|titleId|seriesId)=([0-9]+).*", "$1");
        dto.setTitleId(!idByQuery.equals(detailUrl) ? idByQuery : null);

        return dto;
    }

    /* ----------------- helpers ----------------- */

    private Document get(String url, String cookieString) throws Exception {
        var conn = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(10000);
        if (cookieString != null && !cookieString.isBlank()) {
            conn.header("Cookie", cookieString);
        }
        return conn.get();
    }

    private static LocalDate parseDate(String s) {
        s = s == null ? "" : s.trim().replaceAll("[^0-9\\-./ ]", "");
        String[] pts = {"yyyy.MM.dd","yyyy-MM-dd","yyyy/MM/dd","yyyyMMdd"};
        for (String p : pts) {
            try { return LocalDate.parse(s, DateTimeFormatter.ofPattern(p)); }
            catch (Exception ignored) {}
        }
        return null;
    }

    private static String textOrContent(Element el) {
        if ("meta".equalsIgnoreCase(el.tagName())) return el.attr("content");
        return el.text();
    }

    private static String absSrcOrContent(Element el, Document doc) {
        if ("meta".equalsIgnoreCase(el.tagName())) return el.attr("content");
        return el.hasAttr("abs:src") ? el.attr("abs:src") : el.absUrl("src");
    }
}
