package com.example.AOD.Novel.NaverSeriesNovel;

import com.example.AOD.ingest.CollectorService; // <- 프로젝트 경로에 맞게 조정
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Naver Series(웹소설) 크롤러
 * - 목록(li) 구조 의존 ↓
 * - 상세 링크(productNo) 수집 ↑
 * - 헤더/리퍼러/언어 강화, 쿠키 전달
 * - 원본을 평평한 Map payload로 만들어 raw_items에 저장 (변환/업서트는 배치)
 */
@Component
public class NaverSeriesCrawler {

    private final CollectorService collector;

    public NaverSeriesCrawler(CollectorService collector) {
        this.collector = collector;
    }

    /**
     * 목록 페이지 순회 → 상세 파싱 → raw_items 저장
     * @param baseListUrl 예: https://series.naver.com/novel/top100List.series?rankingTypeCode=DAILY&categoryCode=ALL&page=
     * @param cookieString 필요 시 쿠키 문자열(로그인/성인)
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

            // 1) 상세 링크를 직접 수집 (li 구조와 무관)
            Set<String> detailUrls = new LinkedHashSet<>();
            for (Element a : listDoc.select("a[href*='/novel/detail.series'][href*='productNo=']")) {
                String href = a.attr("href");
                if (!href.startsWith("http")) href = "https://series.naver.com" + href;
                detailUrls.add(href);
            }
            // 2) 폴백: productNo 필터 없이라도 상세 링크 긁기
            if (detailUrls.isEmpty()) {
                for (Element a : listDoc.select("a[href*='/novel/detail.series']")) {
                    String href = a.attr("href");
                    if (!href.startsWith("http")) href = "https://series.naver.com" + href;
                    detailUrls.add(href);
                }
            }

            // 더 이상 수집할 게 없으면 종료
            if (detailUrls.isEmpty()) break;

            for (String detailUrl : detailUrls) {
                NaverSeriesNovelDTO dto = parseDetail(detailUrl, cookieString);
                if (dto.getProductUrl() == null) dto.setProductUrl(detailUrl);

                // 평평한 payload
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

                // raw_items 저장 (hash로 중복 자동 스킵)
                collector.saveRaw(
                        "NaverSeries",
                        "WEBNOVEL",
                        payload,
                        dto.getTitleId(),
                        dto.getProductUrl()
                );
                saved++;
            }

            // 페이지 증가: 링크 기반 수집이라 페이지네이션 엘리먼트에 덜 의존
            page++;
            if (maxPages <= 0) {
                // 무한 순회 방지: 다음 페이지가 현재와 동일 결과면 종료되도록 추가 검출 가능
            }
        }

        return saved;
    }

    /** 상세 페이지 파싱 → DTO */
    public NaverSeriesNovelDTO parseDetail(String detailUrl, String cookieString) throws Exception {
        Document doc = get(detailUrl, cookieString);
        NaverSeriesNovelDTO dto = new NaverSeriesNovelDTO();
        dto.setProductUrl(detailUrl);

        // 제목
        Element titleEl = firstNonNull(
                doc.selectFirst("h2.tit"),
                doc.selectFirst("h3.detail_tit"),
                doc.selectFirst(".detail_view h2"),
                doc.selectFirst("meta[property='og:title']")
        );
        dto.setTitle(titleEl != null ? textOrContent(titleEl) : null);

        // 이미지
        Element imgEl = firstNonNull(
                doc.selectFirst("div.pic_area img"),
                doc.selectFirst(".detail_view .pic img"),
                doc.selectFirst("meta[property='og:image']")
        );
        dto.setImageUrl(imgEl != null ? absSrcOrContent(imgEl, doc) : null);

        // 소개
        Element descEl = firstNonNull(
                doc.selectFirst(".info .desc"),
                doc.selectFirst(".detail_info .summary"),
                doc.selectFirst("meta[name='description']")
        );
        dto.setSynopsis(descEl != null ? textOrContent(descEl) : null);

        // 작가/번역/출판사
        Element authorEl = firstNonNull(
                doc.selectFirst(".info .author a"),
                doc.selectFirst(".detail_info .author a"),
                doc.selectFirst(".author")
        );
        dto.setAuthor(authorEl != null ? authorEl.text().trim() : null);

        Element translatorEl = firstNonNull(
                doc.selectFirst(".info .translator a"),
                doc.selectFirst(".detail_info .translator a"),
                doc.selectFirst(".translator")
        );
        if (translatorEl != null) dto.setTranslator(translatorEl.text().trim());

        Element publisherEl = firstNonNull(
                doc.selectFirst(".info .publisher a"),
                doc.selectFirst(".detail_info .publisher a"),
                doc.selectFirst(".publisher")
        );
        if (publisherEl != null) dto.setPublisher(publisherEl.text().trim());

        // 연재 정보
        Element weekdayEl = firstNonNull(
                doc.selectFirst(".info .weekday"),
                doc.selectFirst(".detail_info .weekday")
        );
        if (weekdayEl != null) dto.setWeekday(weekdayEl.text().trim());

        Element statusEl = firstNonNull(
                doc.selectFirst(".info .status"),
                doc.selectFirst(".detail_info .status")
        );
        if (statusEl != null) dto.setStatus(statusEl.text().trim());

        Element episodeEl = firstNonNull(
                doc.selectFirst(".info .count"),
                doc.selectFirst(".detail_info .episode_count")
        );
        if (episodeEl != null) {
            String digits = episodeEl.text().replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) dto.setEpisodeCount(Integer.parseInt(digits));
        }

        // 시작일
        Element startEl = firstNonNull(
                doc.selectFirst(".info .start_date"),
                doc.selectFirst(".detail_info .date")
        );
        if (startEl != null) dto.setStartedAt(parseDate(startEl.text()));

        // 연령 등급
        Element ageEl = firstNonNull(
                doc.selectFirst(".info .age"),
                doc.selectFirst(".detail_info .age")
        );
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
                // 차단 회피용 헤더 강화
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/124.0 Safari/537.36")
                .referrer("https://series.naver.com/")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .timeout(15000);
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

    @SafeVarargs
    private static <T> T firstNonNull(T... vals) {
        for (T v : vals) if (v != null) return v;
        return null;
    }
}
